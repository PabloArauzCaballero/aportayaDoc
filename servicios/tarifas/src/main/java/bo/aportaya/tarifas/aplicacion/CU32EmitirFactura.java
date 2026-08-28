package bo.aportaya.tarifas.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.tarifas.dominio.CodigoUnicoDeFactura;
import bo.aportaya.tarifas.dominio.PlazoDeContingencia;
import bo.aportaya.tarifas.dominio.puertos.ServicioDeImpuestos;
import bo.aportaya.tarifas.infraestructura.DevengoRepositorio;
import bo.aportaya.tarifas.infraestructura.FacturaRepositorio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-32 · Emitir factura electronica.
 *
 * <p>Que cada comision cobrada tenga su documento fiscal, y que **la caida del servicio
 * de impuestos no detenga la operacion**. Si emitir dependiera de que el SIN responda,
 * un corte de su lado dejaria a la gente sin poder cobrar su turno.
 *
 * <p>La llamada al tercero ocurre **fuera de la transaccion** (invariante 6): primero se
 * pregunta, despues se escribe. Una llamada de red dentro del commit deja la
 * transaccion abierta el tiempo que el otro tarde en contestar, y con ella los candados.
 */
@Service
public class CU32EmitirFactura {

    /** Codigo del evento significativo por caida del servicio. Sale de la norma del SIN. */
    private static final String EVENTO_SERVICIO_CAIDO = "2";

    private final Datos datos;
    private final FacturaRepositorio facturas;
    private final DevengoRepositorio devengos;
    private final ServicioDeImpuestos impuestos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final String nitEmisor;
    private final int sucursal;
    private final int puntoVenta;
    private final Duration plazoTrasElCierre;

    public CU32EmitirFactura(
            Datos datos,
            FacturaRepositorio facturas,
            DevengoRepositorio devengos,
            ServicioDeImpuestos impuestos,
            Outbox outbox,
            Reloj reloj,
            String nitEmisor,
            int sucursal,
            int puntoVenta,
            Duration plazoTrasElCierre) {
        this.datos = datos;
        this.facturas = facturas;
        this.devengos = devengos;
        this.impuestos = impuestos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.nitEmisor = nitEmisor;
        this.sucursal = sucursal;
        this.puntoVenta = puntoVenta;
        this.plazoTrasElCierre = plazoTrasElCierre;
    }

    /**
     * Paso 1: se le pregunta al servicio de impuestos. **Sin transaccion abierta.**
     *
     * <p>Es un metodo aparte y no una linea dentro de {@link #emitir} a proposito: si
     * la llamada viviera adentro, la transaccion quedaria abierta —con sus candados—
     * el tiempo que el tercero tarde en contestar, que es justo lo que no se controla.
     * Quien orquesta llama a este primero y le pasa el resultado al siguiente.
     */
    public ConsultaFiscal consultarAlServicio() {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        Optional<String> cufd = impuestos.cufdVigente(sucursal, puntoVenta, ahora);
        // AP-CU32-01: sin codigo diario vigente no se emite en linea. No es un fallo
        // del usuario ni motivo para revertir el cobro: se emite bajo contingencia.
        return new ConsultaFiscal(cufd.orElse("CUFD-CONTINGENCIA"), cufd.isEmpty());
    }

    /** Paso 2: se escribe con lo que el tercero contesto —o con que no contesto. */
    @Transactional
    public SalidaFactura emitir(EntradaFactura entrada, ConsultaFiscal consulta, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        String cufd = consulta.cufd();
        boolean servicioCaido = consulta.servicioCaido();

        return datos.conContexto(ctx, dsl -> {
            var devengo = devengos.ver(dsl, entrada.devengoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(32, 4), "Ese devengo no existe."));

            // AP-CU32-04 · R-TAR-09: un devengo, una factura. Dos documentos fiscales
            // por el mismo cobro es un problema que solo se arregla anulando uno.
            if (facturas.facturaDe(dsl, devengo.id()).isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(32, 4), "Ese devengo ya tiene factura: se corrige con nota de credito.");
            }
            if (!"COBRADO".equals(devengo.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(32, 4),
                        "El devengo esta " + devengo.estado() + ": se factura lo cobrado, no lo esperado.");
            }

            // El usuario que no cargo datos de facturacion igual recibe su documento:
            // se emite con los minimos y se le pide completar. Revertir el cobro por
            // eso seria castigarlo por un tramite.
            var datosFacturacion = facturas.datosDe(dsl, devengo.usuarioObligadoId());
            if (datosFacturacion.isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(32, 1),
                        "El usuario no tiene datos de facturacion cargados: el cobro NO se revierte, "
                                + "pero el documento no se puede emitir hasta que los complete.");
            }

            UUID contingenciaId = null;
            String estadoFiscal = "VALIDADA";
            if (servicioCaido) {
                // AP-CU32-02 · R-TAR-13: la contingencia se abre con su plazo GUARDADO.
                var abierta = facturas.contingenciaAbierta(dsl, sucursal, puntoVenta);
                if (abierta.isPresent()) {
                    contingenciaId = abierta.get().id();
                } else {
                    var plazo = new PlazoDeContingencia(ahora, plazoTrasElCierre);
                    contingenciaId = facturas.abrirContingencia(
                            dsl,
                            ctx.usuarioId(),
                            EVENTO_SERVICIO_CAIDO,
                            "El servicio de impuestos no responde",
                            sucursal,
                            puntoVenta,
                            cufd,
                            ahora,
                            plazo.limiteDeRegistro(null));
                    outbox.emitir(
                            dsl,
                            new EventoDominio(
                                    "tarifas.contingencia_abierta",
                                    "evento_significativo_sin",
                                    contingenciaId,
                                    Map.of(
                                            "plazoRegistro",
                                                    plazo.limiteDeRegistro(null).toString(),
                                            "puntoVenta", Integer.toString(puntoVenta)),
                                    UUID.fromString(ctx.traza().id())));
                }
                estadoFiscal = "EMITIDA_OFFLINE";
            }

            long numero = facturas.siguienteCorrelativo(dsl, nitEmisor, sucursal, puntoVenta);
            String cuf = CodigoUnicoDeFactura.componer(nitEmisor, sucursal, puntoVenta, numero, ahora)
                    .valor();

            UUID facturaId = facturas.emitir(
                    dsl,
                    devengo.id(),
                    devengo.usuarioObligadoId(),
                    datosFacturacion.get().id(),
                    nitEmisor,
                    sucursal,
                    puntoVenta,
                    numero,
                    cuf,
                    cufd,
                    devengo.montoTotal(),
                    entrada.montoIva(),
                    estadoFiscal,
                    contingenciaId,
                    selloDe(cuf),
                    entrada.urlPdf(),
                    ahora);

            if (contingenciaId != null) {
                facturas.contarDocumentoOffline(dsl, contingenciaId);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "tarifas.factura_emitida",
                            "factura_electronica",
                            facturaId,
                            Map.of(
                                    "cuf",
                                    cuf,
                                    "numeroFactura",
                                    Long.toString(numero),
                                    "estadoFiscal",
                                    estadoFiscal,
                                    "usuarioId",
                                    devengo.usuarioObligadoId().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaFactura(facturaId, cuf, numero, estadoFiscal, contingenciaId);
        });
    }

    /** El sello del documento: lo que permite detectar despues que se altero. */
    private String selloDe(String cuf) {
        try {
            byte[] resumen = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(cuf.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder texto = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }

    public record ConsultaFiscal(String cufd, boolean servicioCaido) {}

    public record EntradaFactura(UUID devengoId, Dinero montoIva, String urlPdf) {}

    public record SalidaFactura(
            UUID facturaId, String cuf, long numeroFactura, String estadoFiscal, UUID eventoSignificativoId) {}
}
