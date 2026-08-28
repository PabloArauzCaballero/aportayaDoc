package bo.aportaya.aportes.aplicacion;

import bo.aportaya.aportes.infraestructura.ProveedorPagoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-99 · Dar de alta un proveedor de pago y enrutar el cobro.
 *
 * <p>**Ninguna credencial entra a la tabla.** La columna se llama
 * {@code referencia_credenciales} y guarda una referencia al almacen de secretos, no
 * el secreto. Una clave de proveedor en una fila de base de datos aparece despues en
 * un respaldo, en un volcado de desarrollo y en la pantalla de cualquiera que tenga
 * lectura — y para entonces ya no hay forma de saber quien la vio.
 *
 * <p>Un proveedor sin consulta de estado deja las ordenes con timeout **en
 * verificacion**, no acreditadas: dar por buena una orden cuyo resultado no se puede
 * consultar es acreditar plata que quiza nunca entro.
 */
@Service
public class CU99EnrutarProveedor {

    /** Lo que delata a una credencial disfrazada de referencia. */
    private static final List<String> PISTAS_DE_SECRETO =
            List.of("sk_", "pk_", "secret", "password", "apikey", "api_key", "bearer", "-----begin");

    private final Datos datos;
    private final ProveedorPagoRepositorio proveedores;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int umbralDeSalud;

    public CU99EnrutarProveedor(
            Datos datos, ProveedorPagoRepositorio proveedores, Outbox outbox, Reloj reloj, int umbralDeSalud) {
        this.datos = datos;
        this.proveedores = proveedores;
        this.outbox = outbox;
        this.reloj = reloj;
        this.umbralDeSalud = umbralDeSalud;
    }

    @Transactional
    public SalidaAlta darDeAlta(EntradaAlta entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        // AP-CU99-05. Se comprueba ANTES de tocar la base: una credencial que llega a
        // la fila ya se filtro, aunque la transaccion se revierta despues — queda en
        // el WAL, en los logs y en cualquier replica.
        String referencia = entrada.referenciaCredenciales() == null
                ? ""
                : entrada.referenciaCredenciales().toLowerCase(java.util.Locale.ROOT);
        if (PISTAS_DE_SECRETO.stream().anyMatch(referencia::contains)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(99, 5),
                    "Eso parece una credencial, no una referencia: las claves van al almacen de secretos.");
        }

        return datos.conContexto(ctx, dsl -> {
            // AP-CU99-01.
            if (proveedores.existeCodigo(dsl, entrada.codigo())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(99, 1), "Ya hay un proveedor con el codigo " + entrada.codigo() + ".");
            }
            // AP-CU99-02 y AP-CU99-03 llegan resueltas: el contrato con el tercero
            // vive en cumplimiento y las pruebas en su propio expediente.
            if (!entrada.tieneContratoVigente()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(99, 2), "No se activa un proveedor sin contrato de tercero vigente.");
            }
            if (!entrada.pruebasCompletas()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(99, 3), "Faltan pruebas de integracion: no se le manda trafico real.");
            }

            UUID proveedorId = proveedores.crear(
                    dsl,
                    entrada.codigo(),
                    entrada.nombre(),
                    entrada.tipo(),
                    entrada.urlBase(),
                    entrada.referenciaCredenciales(),
                    entrada.comisionFija(),
                    entrada.comisionPorcentual(),
                    entrada.soportaWebhook(),
                    entrada.soportaConsultaEstado(),
                    entrada.prioridad());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "aportes.proveedor_activado",
                            "proveedor_pago",
                            proveedorId,
                            Map.of("codigo", entrada.codigo(), "prioridad", Integer.toString(entrada.prioridad())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaAlta(proveedorId, entrada.codigo(), true);
        });
    }

    /**
     * Elige por donde cobrar.
     *
     * <p>Al conmutar de proveedor se conserva **la misma clave de idempotencia**: si
     * el primero cobro y no alcanzo a responder, el segundo tiene que poder reconocer
     * que ya se cobro. Cambiar la clave al conmutar es como se cobra dos veces.
     */
    @Transactional
    public SalidaEnrutamiento enrutar(EntradaEnrutamiento entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var candidatos = proveedores.activos(dsl);
            var elegido = candidatos.stream()
                    .filter(p -> !entrada.yaIntentados().contains(p.codigo()))
                    .filter(p -> entrada.salud(p.codigo()) >= umbralDeSalud)
                    .findFirst();

            if (elegido.isEmpty()) {
                // AP-CU99-04: sin cobertura. No se inventa un proveedor ni se reusa
                // uno que ya fallo para esta orden.
                throw new ErrorDeNegocio(CodigoError.de(99, 4), "No hay proveedor disponible para cobrar esa orden.");
            }

            var proveedor = elegido.get();
            // AP-CU99-06 informativo: si el elegido no consulta estado, un timeout no
            // se puede resolver solo y la orden queda esperando conciliacion.
            String estadoAnteTimeout = proveedor.soportaConsultaEstado() ? "REINTENTABLE" : "EN_VERIFICACION";

            return new SalidaEnrutamiento(
                    proveedor.id(),
                    proveedor.codigo(),
                    entrada.claveIdempotencia(),
                    estadoAnteTimeout,
                    proveedor.soportaConsultaEstado());
        });
    }

    public record EntradaAlta(
            String codigo,
            String nombre,
            String tipo,
            String urlBase,
            String referenciaCredenciales,
            java.math.BigDecimal comisionFija,
            java.math.BigDecimal comisionPorcentual,
            boolean soportaWebhook,
            boolean soportaConsultaEstado,
            int prioridad,
            boolean tieneContratoVigente,
            boolean pruebasCompletas) {}

    public record SalidaAlta(UUID proveedorId, String codigo, boolean activo) {}

    /**
     * Por donde se pide cobrar, y que tan sano esta cada candidato.
     *
     * <p><b>Hueco declarado:</b> no hay tabla de salud de proveedor en {@code aportes}
     * ni endpoint que la publique. La mide quien observa el trafico y la pasa aca. Un
     * proveedor recien activado todavia no tiene medicion: se lo trata como sano
     * porque acaba de pasar contrato y pruebas de integracion, y negarle trafico por
     * no tener historial lo dejaria sin poder generarlo nunca. Esta declarado en
     * {@code planes/informes/carril-3A.md}.
     */
    public record EntradaEnrutamiento(
            String claveIdempotencia, List<String> yaIntentados, Map<String, Integer> saludObservada) {

        /** Lo que vale un proveedor del que todavia no hay medicion. */
        public static final int SIN_MEDICION = 100;

        public EntradaEnrutamiento {
            saludObservada = Map.copyOf(saludObservada);
        }

        public static EntradaEnrutamiento primera(String clave) {
            return new EntradaEnrutamiento(clave, List.of(), Map.of());
        }

        int salud(String codigo) {
            return saludObservada.getOrDefault(codigo, SIN_MEDICION);
        }
    }

    public record SalidaEnrutamiento(
            UUID proveedorId,
            String proveedorCodigo,
            String claveIdempotencia,
            String estadoAnteTimeout,
            boolean puedeConsultarEstado) {}
}
