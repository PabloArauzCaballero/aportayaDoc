package bo.aportaya.garantia.aplicacion;

import bo.aportaya.garantia.dominio.CoberturaAplicable;
import bo.aportaya.garantia.dominio.EstadoDelExpediente;
import bo.aportaya.garantia.infraestructura.DeudaRepositorio;
import bo.aportaya.garantia.infraestructura.ExpedienteRepositorio;
import bo.aportaya.garantia.infraestructura.FondoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-23 · Cubrir un incumplimiento con el fondo.
 *
 * <p>El fondo existe para que el incumplimiento de uno no se lleve puesto al grupo
 * entero: el beneficiario del turno cobra igual. Pero **cubrir todo, siempre, lo
 * vacia** — los topes son lo que hace que el fondo siga estando cuando le toque al
 * siguiente.
 *
 * <p>Cubrir **no perdona**: deja una deuda contra quien incumplio. Si la cobertura
 * borrara la obligacion, el fondo seria un seguro gratuito pagado por los que si pagan.
 */
@Service
public class CU23CubrirIncumplimiento {

    private final Datos datos;
    private final FondoRepositorio fondos;
    private final DeudaRepositorio deudas;
    private final ExpedienteRepositorio expedientes;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int diasParaExigirLaDeuda;
    private final int aniosDePrescripcion;

    public CU23CubrirIncumplimiento(
            Datos datos,
            FondoRepositorio fondos,
            DeudaRepositorio deudas,
            ExpedienteRepositorio expedientes,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.deuda.dias-para-exigir}") int diasParaExigirLaDeuda,
            @Value("${aportaya.deuda.anios-de-prescripcion}") int aniosDePrescripcion) {
        this.datos = datos;
        this.fondos = fondos;
        this.deudas = deudas;
        this.expedientes = expedientes;
        this.outbox = outbox;
        this.reloj = reloj;
        this.diasParaExigirLaDeuda = diasParaExigirLaDeuda;
        this.aniosDePrescripcion = aniosDePrescripcion;
    }

    @Transactional
    public SalidaCobertura cubrir(EntradaCobertura entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var expediente = expedientes
                    .bloquear(dsl, entrada.expedienteId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(23, 1), "Ese expediente no existe."));

            // Una cobertura por expediente. Cubrir dos veces el mismo incumplimiento
            // vacia el fondo por un solo caso.
            var previa = fondos.coberturaDe(dsl, expediente.id());
            if (previa.isPresent()) {
                return new SalidaCobertura(
                        previa.get(),
                        null,
                        Dinero.cero(entrada.montoSolicitado().moneda()),
                        "YA_CUBIERTO",
                        false,
                        false);
            }

            var fondo = fondos.delGrupo(dsl, expediente.grupoId())
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(23, 2), "Ese grupo no tiene fondo de garantia."));
            // AP-CU23-02: un fondo agotado o cerrado no cubre. Denegar por omision.
            if (!"ACTIVO".equals(fondo.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(23, 2), "El fondo esta " + fondo.estado() + ": no puede cubrir.");
            }

            var politica = fondos.politica(dsl, fondo.politicaId(), fondo.moneda())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(23, 2), "El fondo no tiene politica de cobertura vigente."));
            var consumido = fondos.consumido(
                    dsl, fondo.id(), expediente.participanteId(), expediente.periodoId(), fondo.moneda());

            var calculo = CoberturaAplicable.calcular(
                    entrada.montoSolicitado(), politica, consumido, fondo.saldoDisponible(), entrada.diasMora());

            // AP-CU23-03: si ningun limite deja cubrir nada, se registra el intento con
            // su motivo. Un rechazo sin fila deja al grupo sin saber por que no se
            // cubrio, y a nadie a quien reclamarle.
            if (!calculo.cubreAlgo()) {
                UUID rechazada = fondos.registrarCobertura(
                        dsl,
                        fondo.id(),
                        expediente.id(),
                        expediente.obligacionId(),
                        expediente.periodoId(),
                        calculo,
                        "RECHAZADA",
                        null,
                        ahora);
                return new SalidaCobertura(
                        rechazada, null, calculo.montoCubierto(), calculo.limiteQueMando(), false, true);
            }

            // AP-CU23-04: por encima del umbral, una persona decide. Automatizar la
            // cobertura de montos grandes es como se vacia un fondo sin que nadie mire.
            boolean quedaPendiente = calculo.exigeAprobacionManual() && entrada.aprobadaPor() == null;
            String estado = quedaPendiente ? "SOLICITADA" : "APLICADA";

            UUID coberturaId = fondos.registrarCobertura(
                    dsl,
                    fondo.id(),
                    expediente.id(),
                    expediente.obligacionId(),
                    expediente.periodoId(),
                    calculo,
                    estado,
                    entrada.aprobadaPor(),
                    ahora);

            if (quedaPendiente) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "garantia.cobertura_requiere_aprobacion",
                                "cobertura_incumplimiento",
                                coberturaId,
                                Map.of(
                                        "expedienteId", expediente.id().toString(),
                                        "monto", calculo.montoCubierto().toString()),
                                UUID.fromString(ctx.traza().id())));
                return new SalidaCobertura(
                        coberturaId, null, calculo.montoCubierto(), calculo.limiteQueMando(), true, true);
            }

            Dinero saldoDespues = fondo.saldoDisponible().menos(calculo.montoCubierto());
            if (!fondos.moverSaldo(
                    dsl,
                    fondo.id(),
                    Dinero.cero(fondo.moneda()).menos(calculo.montoCubierto()),
                    calculo.montoCubierto(),
                    fondo.version())) {
                throw new ErrorDeNegocio(CodigoError.de(23, 2), "Otra cobertura movio el fondo primero: reintenta.");
            }
            fondos.registrarMovimiento(
                    dsl,
                    fondo.id(),
                    "COBERTURA_APLICADA",
                    calculo.montoCubierto(),
                    saldoDespues,
                    "COBERTURA",
                    coberturaId,
                    "Cobertura del expediente " + expediente.codigoExpediente(),
                    ctx.usuarioId(),
                    ahora);

            // Cubrir NO perdona: queda la deuda contra quien incumplio.
            UUID deudaId = deudas.registrarDeuda(
                    dsl,
                    expediente.usuarioId(),
                    expediente.participanteId(),
                    expediente.grupoId(),
                    expediente.id(),
                    coberturaId,
                    "FONDO_GARANTIA",
                    calculo.montoCubierto(),
                    ahora.toLocalDate().plusDays(diasParaExigirLaDeuda),
                    ahora.toLocalDate().plusYears(aniosDePrescripcion));

            expedientes.registrarTransicion(
                    dsl,
                    expediente.id(),
                    expedientes.estadoCorriente(dsl, expediente.id()),
                    EstadoDelExpediente.CUBIERTO_POR_GARANTIA,
                    "Cubierto por el fondo, con deuda a recuperar",
                    calculo.montoCubierto(),
                    ctx.usuarioId(),
                    entrada.aprobadaPor() == null,
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.incumplimiento_cubierto",
                            "cobertura_incumplimiento",
                            coberturaId,
                            Map.of(
                                    "expedienteId", expediente.id().toString(),
                                    "deudaId", deudaId.toString(),
                                    "montoCubierto", calculo.montoCubierto().toString(),
                                    "limiteQueMando", calculo.limiteQueMando(),
                                    "saldoDelFondo", saldoDespues.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCobertura(
                    coberturaId, deudaId, calculo.montoCubierto(), calculo.limiteQueMando(), false, true);
        });
    }

    public record EntradaCobertura(UUID expedienteId, Dinero montoSolicitado, int diasMora, UUID aprobadaPor) {}

    public record SalidaCobertura(
            UUID coberturaId,
            UUID deudaId,
            Dinero montoCubierto,
            String limiteQueMando,
            boolean requiereAprobacion,
            boolean esNueva) {}
}
