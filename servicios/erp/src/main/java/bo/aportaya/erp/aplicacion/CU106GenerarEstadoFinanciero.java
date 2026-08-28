package bo.aportaya.erp.aplicacion;

import bo.aportaya.erp.dominio.CuadreContable;
import bo.aportaya.erp.infraestructura.PeriodoRepositorio;
import bo.aportaya.erp.infraestructura.PresupuestoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-106 · Generar el estado financiero del periodo.
 *
 * <p>**Sale de los saldos, no de un total guardado.** Un balance que arrastra un numero
 * de otra tabla puede quedar desactualizado sin que nadie lo note, y el dia que alguien
 * lo compare contra el mayor la diferencia no se explica.
 *
 * <p>**Un estado por periodo y tipo** (R-CTB-08). Dos balances del mismo mes se
 * contradicen entre si y no hay forma de decir cual se entrego.
 *
 * <p>Y **la ecuacion tiene que cerrar**: activo = pasivo + patrimonio. Publicar un
 * balance que no cierra es publicar un numero que nadie puede usar.
 */
@Service
public class CU106GenerarEstadoFinanciero {

    private final Datos datos;
    private final PresupuestoRepositorio presupuestos;
    private final PeriodoRepositorio periodos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU106GenerarEstadoFinanciero(
            Datos datos, PresupuestoRepositorio presupuestos, PeriodoRepositorio periodos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.presupuestos = presupuestos;
        this.periodos = periodos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaEstado generar(UUID periodoId, String tipo, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var periodo = periodos.periodoPorId(dsl, periodoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(106, 2), "Ese periodo no existe."));

            var yaGenerado = presupuestos.estadoDe(dsl, periodoId, tipo);
            if (yaGenerado.isPresent()) {
                // R-CTB-08: uno por periodo y tipo. Se devuelve el que hay en vez de
                // generar un segundo que lo contradiga.
                throw new ErrorDeNegocio(
                        CodigoError.de(106, 1),
                        "Ya existe un " + tipo + " del periodo " + periodo.mes() + ".",
                        Map.of("estadoId", yaGenerado.get().toString()));
            }

            var saldos = presupuestos.saldosDelPeriodo(dsl, periodoId).stream()
                    .map(s -> new CuadreContable.SaldoDeCuenta(s.codigo(), s.tipo(), s.saldo()))
                    .toList();

            var estado =
                    "BALANCE_GENERAL".equals(tipo) ? CuadreContable.balance(saldos) : CuadreContable.resultados(saldos);

            // AP-CU106-03: un balance que no cierra no se publica. Guardarlo igual
            // dejaria un documento firmado con una inconsistencia adentro.
            if (!estado.ecuacionCierra()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(106, 2),
                        "La ecuacion contable no cierra: diferencia de "
                                + estado.diferencia().toPlainString() + ".");
            }

            String datosJson = serializar(estado);
            String hash = sha256(datosJson);
            UUID id = presupuestos.guardarEstado(dsl, periodoId, tipo, ctx.usuarioId(), datosJson, hash, ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.estado_financiero_generado",
                            "estado_financiero_generado",
                            id,
                            Map.of(
                                    "tipo",
                                    tipo,
                                    "mes",
                                    Integer.toString(periodo.mes()),
                                    "hashContenido",
                                    hash,
                                    "cuentasConsideradas",
                                    Integer.toString(saldos.size())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEstado(id, tipo, estado, hash);
        });
    }

    /**
     * Comprueba que una plantilla de asiento sea aplicable.
     *
     * <p>**Ninguna linea puede apuntar a una cuenta sumarizadora** (R-CTB-02): una
     * sumarizadora es un total, no un destino, y {@code fn_ctb_cuenta_de_movimiento} lo
     * rechazaria recien al escribir el movimiento — con la transaccion entera ya armada.
     * Decirlo al validar la plantilla evita descubrirlo en el peor momento.
     *
     * <p>Y las lineas tienen que estar **balanceadas**: al menos un debe y al menos un
     * haber. Una plantilla con dos debes no puede producir un asiento cuadrado.
     */
    @Transactional
    public Plantilla validarPlantilla(UUID plantillaId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var lineas = presupuestos.lineasDePlantilla(dsl, plantillaId);
            if (lineas.isEmpty()) {
                throw new ErrorDeNegocio(CodigoError.de(106, 2), "Esa plantilla no tiene lineas.");
            }
            var sumarizadoras = lineas.stream()
                    .filter(l -> !l.esCuentaDeMovimiento())
                    .map(l -> "orden " + l.orden())
                    .toList();
            if (!sumarizadoras.isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(106, 2),
                        "La plantilla apunta a cuentas sumarizadoras en " + String.join(", ", sumarizadoras)
                                + " (R-CTB-02).");
            }
            long debes = lineas.stream()
                    .filter(l -> "DEBE".equals(l.tipoMovimiento()))
                    .count();
            long haberes = lineas.size() - debes;
            if (debes == 0 || haberes == 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(106, 2),
                        "La plantilla no esta balanceada: %d debe y %d haber.".formatted(debes, haberes));
            }
            return new Plantilla(plantillaId, lineas.size(), (int) debes, (int) haberes);
        });
    }

    public record Plantilla(UUID plantillaId, int lineas, int debes, int haberes) {}

    /**
     * El JSON del estado, en forma canonica.
     *
     * <p>Los renglones van en el orden en que se calcularon y los importes como cadena:
     * el hash tiene que poder recomputarse desde el mismo estado, y un `double` daria
     * otro hash en otra maquina.
     */
    private String serializar(CuadreContable.Estado estado) {
        var sb = new StringBuilder("{\"tipo\":\"").append(estado.tipo()).append("\",\"renglones\":[");
        for (int i = 0; i < estado.renglones().size(); i++) {
            var r = estado.renglones().get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"concepto\":\"")
                    .append(r.concepto())
                    .append("\",\"monto\":\"")
                    .append(r.monto().toPlainString())
                    .append("\"}");
        }
        return sb.append("]}").toString();
    }

    private String sha256(String contenido) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8));
            var texto = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }

    public record SalidaEstado(UUID estadoId, String tipo, CuadreContable.Estado estado, String hashContenido) {}
}
