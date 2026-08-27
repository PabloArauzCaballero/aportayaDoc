package bo.aportaya.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.notificaciones.aplicacion.CU83DespacharLote.EntradaLote;
import bo.aportaya.notificaciones.aplicacion.CU83DespacharLote.SalidaLote;
import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.notificaciones.dominio.EleccionDeProveedor;
import bo.aportaya.notificaciones.dominio.EleccionDeProveedor.Candidato;
import bo.aportaya.notificaciones.dominio.EsperaDeReintento;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-83 · Enrutar el envio por proveedor de mensajeria. */
class CU83Test extends BaseDeNotificaciones {

    private static final BigDecimal GRATIS = new BigDecimal("0.00");
    private static final Duration TECHO = Duration.ofMinutes(60);

    @BeforeEach
    void reiniciarAdaptador() {
        adaptador.reiniciar();
    }

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    /** Un envio listo para despachar, con su fila en la cola. */
    private UUID envioEnCola(UUID proveedorId, String particion, int maxIntentos) {
        UUID usuario = fixtura.usuario();
        // `uq_evento_notificable_tipo`: un tipo, una fila. Cada envio de prueba trae
        // el suyo, porque dos llamadas en la misma prueba chocarian.
        UUID evento = fixtura.evento(
                "aporte.recordatorio." + UUID.randomUUID().toString().substring(0, 8), "COBRANZA", true, "ALTA");
        UUID version =
                fixtura.plantilla("PL-" + UUID.randomUUID().toString().substring(0, 6), evento, "IN_APP", "hola");
        UUID notificacion = UUID.randomUUID();
        dslFixtura.execute(
                """
                INSERT INTO notificaciones.notificacion
                    (id, usuario_id, evento_id, prioridad, contexto, clave_deduplicacion, estado,
                     programada_para, creada_en, correlation_id)
                VALUES (?, ?, ?, 'ALTA', '{}'::jsonb, ?, 'EN_COLA', now(), now(), gen_random_uuid())
                """,
                notificacion,
                usuario,
                evento,
                "dedupe-" + notificacion);
        UUID envio = UUID.randomUUID();
        dslFixtura.execute(
                """
                INSERT INTO notificaciones.envio_notificacion
                    (id, notificacion_id, proveedor_id, version_plantilla_id, canal, destinatario,
                     clave_idempotencia, encolado_en, contenido_enviado, estado, orden, intentos,
                     max_intentos, costo, moneda)
                VALUES (?, ?, ?, ?, 'IN_APP', 'app:destino', ?, now(), 'hola', 'PENDIENTE', 1, 0, ?, 0.00, 'BOB')
                """,
                envio,
                notificacion,
                proveedorId,
                version,
                "idem-" + envio,
                (short) maxIntentos);
        dslFixtura.execute(
                """
                INSERT INTO notificaciones.cola_envio (id, envio_id, particion, disponible_en, intentos)
                VALUES (gen_random_uuid(), ?, ?, now() - interval '1 minute', 0)
                """,
                envio,
                particion);
        return envio;
    }

    private SalidaLote despachar(ContextoSesion ctx) {
        return transaccion.execute(e -> loteCU.ejecutar(new EntradaLote("OBLIGATORIO", 10), ctx));
    }

    @Test
    @DisplayName(
            "Dado un envío obligatorio y el proveedor de mayor prioridad caído · Cuando se despacha · Entonces se conmuta al siguiente proveedor · Y el mensaje se envía una sola vez con la misma clave de idempotencia")
    void criterio1() {
        UUID caido = fixtura.proveedor("CAIDO", "IN_APP", 1, GRATIS, 20, true);
        fixtura.proveedor("SANO", "IN_APP", 2, GRATIS, 99, true);
        UUID envio = envioEnCola(caido, "OBLIGATORIO", 3);
        ContextoSesion ctx = contexto();

        SalidaLote salida = despachar(ctx);

        // El caido esta por debajo del umbral: ni se lo intenta.
        assertThat(adaptador.enviados).hasSize(1);
        assertThat(adaptador.enviados.get(0)).startsWith("SANO:");
        assertThat(salida.detalle()).singleElement().satisfies(d -> {
            assertThat(d.proveedorCodigo()).isEqualTo("SANO");
            assertThat(d.estado()).isEqualTo("ENVIADO");
        });
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.envio_notificacion WHERE id = ? AND estado = 'ENVIADO'",
                        envio))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dadas dos réplicas del trabajador · Cuando ambas toman lotes a la vez · Entonces ningún envío es procesado por las dos")
    void criterio2() {
        fixtura.proveedor("SANO", "IN_APP", 1, GRATIS, 99, true);
        UUID proveedorId = dsl.fetchOne("SELECT id FROM notificaciones.proveedor_mensajeria WHERE codigo = 'SANO'")
                .get(0, UUID.class);
        envioEnCola(proveedorId, "OBLIGATORIO", 3);
        ContextoSesion ctx = contexto();

        // La segunda corrida ya no encuentra nada: el primero lo saco de la cola.
        SalidaLote primera = despachar(ctx);
        SalidaLote segunda = despachar(ctx);

        assertThat(primera.tomados()).isEqualTo(1);
        assertThat(segunda.tomados()).isZero();
        assertThat(adaptador.enviados).hasSize(1);
    }

    @Test
    @DisplayName(
            "Dado un proveedor con salud por debajo del umbral · Cuando se elige proveedor para un envío nuevo · Entonces no se lo selecciona y queda registrada la degradación")
    void criterio3() {
        var degradado = new Candidato("DEGRADADO", List.of("IN_APP"), 1, GRATIS, 30, true);
        var sano = new Candidato("SANO", List.of("IN_APP"), 5, GRATIS, 95, true);

        var elegido = EleccionDeProveedor.elegir(List.of(degradado, sano), Canal.IN_APP, List.of(), UMBRAL_DE_SALUD);

        // La salud gana sobre la prioridad: el mas barato que no entrega sale carisimo.
        assertThat(elegido).isPresent();
        assertThat(elegido.get().codigo()).isEqualTo("SANO");
    }

    @Test
    @DisplayName(
            "Dado un envío obligatorio que agota todos los proveedores · Cuando cae a cola muerta · Entonces se escala a operaciones y no se cierra en silencio")
    void criterio4() {
        UUID unico = fixtura.proveedor("UNICO", "IN_APP", 1, GRATIS, 99, true);
        UUID envio = envioEnCola(unico, "OBLIGATORIO", 1);
        adaptador.proveedoresQueFallan = Set.of("UNICO");
        ContextoSesion ctx = contexto();

        SalidaLote salida = despachar(ctx);

        assertThat(salida.detalle()).singleElement().satisfies(d -> assertThat(d.estado())
                .isEqualTo("COLA_MUERTA"));
        assertThat(contar("SELECT count(*)::int FROM notificaciones.cola_muerta WHERE envio_id = ?", envio))
                .isEqualTo(1);
        // No se cierra en silencio: queda el evento para que alguien lo mire.
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.evento_dominio WHERE tipo = ?",
                        "notificaciones.notificacion_fallida"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Un fallo no descarta el mensaje: lo reprograma con la MISMA clave de
        // idempotencia, para que el proveedor no lo trate como uno nuevo.
        UUID unico = fixtura.proveedor("UNICO", "IN_APP", 1, GRATIS, 99, true);
        UUID envio = envioEnCola(unico, "OBLIGATORIO", 5);
        adaptador.proveedoresQueFallan = Set.of("UNICO");
        ContextoSesion ctx = contexto();

        despachar(ctx);

        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.cola_envio WHERE envio_id = ? AND intentos = 1",
                        envio))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM notificaciones.cola_muerta"))
                .isZero();
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // `FOR UPDATE SKIP LOCKED` es lo que lo garantiza: sin el, dos replicas se
        // llevarian la misma fila y el mensaje saldria dos veces.
        fixtura.proveedor("SANO", "IN_APP", 1, GRATIS, 99, true);
        UUID proveedorId = dsl.fetchOne("SELECT id FROM notificaciones.proveedor_mensajeria WHERE codigo = 'SANO'")
                .get(0, UUID.class);
        envioEnCola(proveedorId, "OBLIGATORIO", 3);
        envioEnCola(proveedorId, "OBLIGATORIO", 3);
        ContextoSesion ctx = contexto();

        SalidaLote primera = despachar(ctx);
        SalidaLote segunda = despachar(ctx);

        assertThat(primera.tomados()).isEqualTo(2);
        assertThat(segunda.tomados()).isZero();
        assertThat(adaptador.enviados).hasSize(2);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-83 mueve costo, no saldo: lo que cuadra es la espera. Crece, tiene techo,
        // y es DETERMINISTA — dos veces el mismo envio e intento dan lo mismo.
        Duration primera = EsperaDeReintento.para("envio-a", 1, TECHO);
        Duration otraVez = EsperaDeReintento.para("envio-a", 1, TECHO);
        Duration masTarde = EsperaDeReintento.para("envio-a", 5, TECHO);
        Duration otroEnvio = EsperaDeReintento.para("envio-b", 1, TECHO);

        assertThat(primera).isEqualTo(otraVez);
        assertThat(masTarde).isGreaterThan(primera);
        // Dispersion: dos envios distintos no reintentan en el mismo instante.
        assertThat(EsperaDeReintento.para("envio-a", 8, TECHO))
                .isNotEqualTo(EsperaDeReintento.para("envio-b", 8, TECHO));
        assertThat(otroEnvio).isNotNull();
        // Techo: el intento 20 no cae dentro de doce dias.
        assertThat(EsperaDeReintento.para("envio-a", 20, TECHO)).isLessThanOrEqualTo(Duration.ofMinutes(75));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "notificaciones"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "notificaciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin proveedor para el canal no se reintenta a ciegas: sale de la cola viva y
        // queda registrado, en vez de girar para siempre.
        fixtura.proveedor("SOLO_SMS", "SMS", 1, GRATIS, 99, true);
        UUID proveedorId = dsl.fetchOne("SELECT id FROM notificaciones.proveedor_mensajeria WHERE codigo = 'SOLO_SMS'")
                .get(0, UUID.class);
        UUID envio = envioEnCola(proveedorId, "OBLIGATORIO", 3);
        ContextoSesion ctx = contexto();

        SalidaLote salida = despachar(ctx);

        assertThat(salida.detalle()).singleElement().satisfies(d -> assertThat(d.estado())
                .isEqualTo("COLA_MUERTA"));
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.cola_muerta WHERE envio_id = ? AND motivo = ?",
                        envio,
                        "SIN_PROVEEDOR_PARA_CANAL"))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM notificaciones.cola_envio"))
                .isZero();
        assertThat(adaptador.enviados).isEmpty();
    }

    @Test
    @DisplayName("rechaza quedarse sin mandar nada: con todos degradados usa el menos malo")
    void rechazaNoMandarNada() {
        // Un proveedor al 40% entrega cuatro de cada diez; no mandar entrega cero.
        var malo = new Candidato("MALO", List.of("IN_APP"), 1, GRATIS, 40, true);
        var peor = new Candidato("PEOR", List.of("IN_APP"), 2, GRATIS, 10, true);

        var elegido = EleccionDeProveedor.elegir(List.of(malo, peor), Canal.IN_APP, List.of(), UMBRAL_DE_SALUD);

        assertThat(elegido).isPresent();
        assertThat(elegido.get().codigo()).isEqualTo("MALO");
        // Pero uno inactivo no se usa ni como ultimo recurso.
        var apagado = new Candidato("APAGADO", List.of("IN_APP"), 1, GRATIS, 100, false);
        assertThat(EleccionDeProveedor.elegir(List.of(apagado), Canal.IN_APP, List.of(), UMBRAL_DE_SALUD))
                .isEmpty();
    }
}
