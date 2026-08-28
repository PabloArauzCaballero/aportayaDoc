package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.infraestructura.CierreDiarioRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.ConciliacionRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-51 · Ejecutar el cierre diario.
 *
 * <p>**Vive en nucleo-financiero y no en aportes**, aunque planes/07 lo asigne al
 * carril 3A: escribe {@code cierre_diario} y lee {@code asiento_contable}, y las dos
 * tablas estan en este esquema. Implementarlo en aportes exigiria escribir un esquema
 * ajeno, que es el invariante 11. Es la misma mudanza que CU-40, por la misma razon, y
 * queda declarada.
 *
 * <p>El conteo de excepciones de conciliacion **llega desde afuera**: esas viven en
 * `aportes` y este servicio no las lee. Quien dispara el cierre las trae resueltas.
 *
 * <p>Cerrar el dia es decir «esto ya no se discute». Por eso no se cierra con
 * problemas abiertos: una excepcion sin resolver o un asiento sin confirmar significan
 * que todavia no se sabe cuanto hay, y firmar un cierre sobre un numero que no se
 * conoce es peor que no cerrar.
 */
@Service
public class CU51EjecutarCierreDiario {

    private final Datos datos;
    private final CierreDiarioRepositorio cierres;
    private final ConciliacionRepositorio conciliaciones;
    private final CuentaBilleteraRepositorio cuentas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU51EjecutarCierreDiario(
            Datos datos,
            CierreDiarioRepositorio cierres,
            ConciliacionRepositorio conciliaciones,
            CuentaBilleteraRepositorio cuentas,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.cierres = cierres;
        this.conciliaciones = conciliaciones;
        this.cuentas = cuentas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaCierre ejecutar(EntradaCierre entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU51-04: el dia ya cerrado devuelve lo que hay. No es un error —el
            // planificador reintenta— y reescribir los saldos borraria la foto del
            // dia, que es justamente lo que el cierre existe para conservar.
            var existente = cierres.delDia(dsl, entrada.fecha());
            if (existente.isPresent()) {
                return new SalidaCierre(
                        existente.get().id(), entrada.fecha(), existente.get().cuadrado(), 0, true, null);
            }

            // AP-CU51-03.
            int asientosPendientes = cierres.asientosSinConfirmar(dsl, entrada.fecha());
            if (asientosPendientes > 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(51, 3),
                        "Hay " + asientosPendientes + " asiento(s) sin confirmar del " + entrada.fecha()
                                + ": la contabilidad del dia todavia se esta escribiendo.");
            }

            // AP-CU51-01 y AP-CU51-02 llegan resueltos desde afuera.
            boolean cuadrado = entrada.excepcionesAbiertas() == 0 && entrada.custodiaCuadrada();

            UUID cierreId = cierres.registrar(
                    dsl,
                    entrada.fecha(),
                    entrada.totalRecaudado(),
                    entrada.totalConciliado(),
                    entrada.totalExcepciones(),
                    entrada.cantidadPagos(),
                    cuadrado,
                    ctx.usuarioId(),
                    ahora);

            // Un saldo diario por cada cuenta activa: es la foto contra la que despues
            // se emite cada extracto y se concilia la custodia.
            for (UUID cuentaId : cierres.cuentasActivas(dsl)) {
                var cuenta = cuentas.ver(dsl, cuentaId).orElseThrow();
                conciliaciones.cerrarSaldoDelDia(
                        dsl,
                        cuentaId,
                        entrada.fecha(),
                        cuenta.disponible(),
                        cuenta.retenido(),
                        0,
                        selloDe(cuentaId, entrada.fecha()),
                        ahora);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            cuadrado
                                    ? "nucleo_financiero.dia_cerrado"
                                    : "nucleo_financiero.dia_cerrado_con_observaciones",
                            "cierre_diario",
                            cierreId,
                            Map.of(
                                    "fecha", entrada.fecha().toString(),
                                    "cuadrado", Boolean.toString(cuadrado),
                                    "excepcionesAbiertas", Integer.toString(entrada.excepcionesAbiertas())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCierre(
                    cierreId,
                    entrada.fecha(),
                    cuadrado,
                    entrada.excepcionesAbiertas(),
                    false,
                    cuadrado
                            ? null
                            : "Quedan " + entrada.excepcionesAbiertas()
                                    + " excepcion(es) de conciliacion abiertas o la custodia no cuadra.");
        });
    }

    /** El sello del saldo del dia: lo que permite detectar despues una alteracion. */
    private String selloDe(UUID cuentaId, LocalDate fecha) {
        String material = cuentaId + "|" + fecha;
        try {
            byte[] resumen = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder texto = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }

    public record EntradaCierre(
            LocalDate fecha,
            BigDecimal totalRecaudado,
            BigDecimal totalConciliado,
            BigDecimal totalExcepciones,
            int cantidadPagos,
            int excepcionesAbiertas,
            boolean custodiaCuadrada) {}

    public record SalidaCierre(
            UUID cierreId,
            LocalDate fecha,
            boolean cuadrado,
            int excepcionesAbiertas,
            boolean yaExistia,
            String motivoDelDescuadre) {}
}
