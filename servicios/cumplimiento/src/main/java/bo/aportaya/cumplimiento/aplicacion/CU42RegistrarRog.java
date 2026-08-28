package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.ConceptoRog;
import bo.aportaya.cumplimiento.dominio.UmbralAlcanzado;
import bo.aportaya.cumplimiento.infraestructura.OperacionRelevanteRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-42 · Detectar umbral y registrar ROG.
 *
 * <p>El hermano silencioso de CU-41: **el usuario no ve estos registros y no se le pide
 * nada**. Un ROG no acusa a nadie —es una operacion que la norma manda informar por su
 * tipo—, y avisarle al titular convertiria un tramite en una sospecha.
 *
 * <p>Una misma operacion puede disparar PCC-01 y ROG-03 a la vez, y entonces **son dos
 * registros distintos**. Fusionarlos ahorraria una fila y perderia que son dos
 * obligaciones con articulos, plazos y formatos distintos.
 */
@Service
public class CU42RegistrarRog {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Datos datos;
    private final OperacionRelevanteRepositorio operaciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU42RegistrarRog(Datos datos, OperacionRelevanteRepositorio operaciones, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.operaciones = operaciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaRog registrar(CU41RegistrarPcc01.EntradaUmbral entrada, ContextoSesion ctx) {
        if (entrada.usuarioId() == null) {
            // Operativa propia: no hay titular a quien atribuirle la operacion.
            return new SalidaRog(List.of(), entrada.fecha().format(PERIODO));
        }
        // AP-CU42-01 · R-UIF-04.
        if (!"USD".equals(entrada.moneda()) && entrada.tipoDeCambio() == null) {
            throw new ErrorDeNegocio(
                    CodigoError.de(42, 1),
                    "No hay tipo de cambio " + entrada.moneda() + " -> USD al " + entrada.fecha() + ".");
        }

        BigDecimal tipoDeCambio = "USD".equals(entrada.moneda()) ? BigDecimal.ONE : entrada.tipoDeCambio();
        BigDecimal montoUsd = UmbralAlcanzado.aUsd(entrada.monto(), entrada.moneda(), tipoDeCambio);

        return datos.conContexto(ctx, dsl -> {
            var umbrales = operaciones.umbralesVigentes(dsl, entrada.concepto(), entrada.fecha());
            var registros = new ArrayList<CU41RegistrarPcc01.Registro>();

            for (var umbral : umbrales) {
                if (!ConceptoRog.esRog(umbral.formulario())) {
                    continue;
                }
                var medicion = UmbralAlcanzado.medir(
                        montoUsd, entrada.acumuladoEnVentana(), umbral.umbralUsd(), umbral.esAcumulado());
                if (!medicion.alcanza()) {
                    continue;
                }
                var yaEsta = operaciones.registroDe(dsl, entrada.transaccionId(), umbral.id());
                if (yaEsta.isPresent()) {
                    // AP-CU42-02: el reintento devuelve lo que hay. La red duplica; el
                    // reporte a la UIF no puede duplicarse con ella.
                    registros.add(new CU41RegistrarPcc01.Registro(
                            yaEsta.get(), umbral.formulario(), umbral.esAcumulado(), medicion.montoMedido(), false));
                    continue;
                }

                UUID id = operaciones.registrar(
                        dsl,
                        new OperacionRelevanteRepositorio.Registro(
                                entrada.usuarioId(),
                                entrada.transaccionId(),
                                umbral.id(),
                                umbral.esAcumulado() ? entrada.inicioDeVentanaId() : null,
                                umbral.formulario(),
                                umbral.concepto(),
                                umbral.esAcumulado(),
                                umbral.esAcumulado() ? entrada.ventanaDesde() : null,
                                umbral.esAcumulado() ? entrada.fecha() : null,
                                entrada.monto(),
                                entrada.moneda(),
                                medicion.montoMedido(),
                                tipoDeCambio,
                                montoUsd,
                                umbral.umbralUsd(),
                                entrada.exento(),
                                // Un ROG no lleva declaracion del titular, asi que
                                // ck_operelev_declaracion no le exige nada: la clausula
                                // `formulario <> 'PCC-01'` lo deja pasar.
                                entrada.exento() ? entrada.motivoDeExencion() : null,
                                null,
                                null,
                                entrada.fecha().format(PERIODO),
                                entrada.ocurridaEn()));

                registros.add(new CU41RegistrarPcc01.Registro(
                        id, umbral.formulario(), umbral.esAcumulado(), medicion.montoMedido(), true));

                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "cumplimiento.uif_operacion_general",
                                "registro_operacion_relevante",
                                id,
                                Map.of(
                                        "usuarioId", entrada.usuarioId().toString(),
                                        "formulario", umbral.formulario(),
                                        "esAcumulada", Boolean.toString(umbral.esAcumulado()),
                                        "montoUsd", montoUsd.toPlainString(),
                                        "baseNormativa", umbral.baseNormativa()),
                                UUID.fromString(ctx.traza().id())));
            }
            return new SalidaRog(List.copyOf(registros), entrada.fecha().format(PERIODO));
        });
    }

    public record SalidaRog(List<CU41RegistrarPcc01.Registro> registros, String periodoRemision) {}
}
