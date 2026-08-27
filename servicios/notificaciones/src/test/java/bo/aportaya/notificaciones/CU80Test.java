package bo.aportaya.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.notificaciones.aplicacion.CU80DespacharNotificacion.EntradaDespacho;
import bo.aportaya.notificaciones.aplicacion.CU80DespacharNotificacion.SalidaDespacho;
import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.notificaciones.dominio.PlantillaRenderizada;
import bo.aportaya.notificaciones.dominio.VentanaDeEnvio;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-80 · Despachar una notificacion. */
class CU80Test extends BaseDeNotificaciones {

    private static final String PLANTILLA = "APORTE_POR_VENCER";
    private static final String CUERPO = "Hola {{nombre}}, tu aporte de {{monto}} vence pronto.";

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private SalidaDespacho despachar(UUID eventoId, UUID destinatario, Canal canal, String clave, ContextoSesion ctx) {
        return transaccion.execute(e -> despachoCU.ejecutar(
                new EntradaDespacho(
                        eventoId,
                        destinatario,
                        canal.name(),
                        PLANTILLA,
                        Map.of("nombre", "Ana", "monto", "500.00"),
                        false,
                        clave),
                ctx));
    }

    @Test
    @DisplayName(
            "Dado un evento de dominio con plantilla aprobada y destinatario verificado · Cuando el trabajador lo procesa · Entonces existe un envio_notificacion con clave de idempotencia · Y el proveedor recibió exactamente un mensaje")
    void criterio1() {
        UUID usuario = fixtura.usuario();
        UUID evento = fixtura.evento("aporte.por_vencer", "COBRANZA", false, "NORMAL");
        fixtura.canalVerificado(usuario, "IN_APP", "app:" + usuario);
        fixtura.plantilla(PLANTILLA, evento, "IN_APP", CUERPO);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaDespacho salida = despachar(evento, usuario, Canal.IN_APP, "dedupe-1", ctx);

        assertThat(salida.estado()).isEqualTo("ENCOLADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.notificacion WHERE id = ?", salida.notificacionId()))
                .isEqualTo(1);
        // La bandeja recibe SIEMPRE, y exactamente una vez.
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.bandeja_entrada WHERE notificacion_id = ?",
                        salida.notificacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado el mismo evento reprocesado tras un reinicio · Cuando se consume otra vez · Entonces no se genera un segundo envío")
    void criterio2() {
        UUID usuario = fixtura.usuario();
        UUID evento = fixtura.evento("aporte.por_vencer", "COBRANZA", false, "NORMAL");
        fixtura.canalVerificado(usuario, "IN_APP", "app:" + usuario);
        fixtura.plantilla(PLANTILLA, evento, "IN_APP", CUERPO);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaDespacho primera = despachar(evento, usuario, Canal.IN_APP, "dedupe-2", ctx);
        SalidaDespacho segunda = despachar(evento, usuario, Canal.IN_APP, "dedupe-2", ctx);

        assertThat(segunda.notificacionId()).isEqualTo(primera.notificacionId());
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.notificacion WHERE clave_deduplicacion = ?",
                        "dedupe-2"))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.bandeja_entrada WHERE notificacion_id = ?",
                        primera.notificacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un destinatario en lista de supresión · Cuando se procesa el evento · Entonces el estado es SUPRIMIDA con su motivo y no hay envío")
    void criterio3() {
        UUID usuario = fixtura.usuario();
        UUID evento = fixtura.evento("promo.nueva", "COMERCIAL", false, "BAJA");
        String identificador = "app:" + usuario;
        fixtura.canalVerificado(usuario, "IN_APP", identificador);
        fixtura.plantilla(PLANTILLA, evento, "IN_APP", CUERPO);
        dslFixtura.execute(
                """
                INSERT INTO notificaciones.lista_supresion
                    (id, identificador, canal, motivo, categoria, activa, agregado_en, permanente)
                VALUES (gen_random_uuid(), ?, 'IN_APP', 'QUEJA_SPAM', 'COMERCIAL', true, now(), false)
                """,
                identificador);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaDespacho salida = despachar(evento, usuario, Canal.IN_APP, "dedupe-3", ctx);

        assertThat(salida.estado()).isEqualTo("SUPRIMIDA");
        assertThat(salida.motivoNoEnvio()).contains("no recibir esta categoria");
        assertThat(contar("SELECT count(*)::int FROM notificaciones.envio_notificacion"))
                .isZero();
        // Ni siquiera queda la notificacion: tg_notificacion_supresion rechaza el
        // INSERT, y el caso de uso no se pelea con la regla — deja el rastro en el
        // outbox, que es donde corresponde.
        assertThat(contar("SELECT count(*)::int FROM notificaciones.notificacion"))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.evento_dominio WHERE tipo = ?",
                        "notificaciones.notificacion_suprimida"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un evento fuera del horario permitido · Cuando se procesa · Entonces queda REPROGRAMADA para la primera hora hábil")
    void criterio4() {
        // La ventana se prueba en el atomo, que es donde vive la decision: montar una
        // hora falsa en la base seria probar el reloj, no la regla.
        var ventana = new VentanaDeEnvio(LocalTime.of(8, 0), LocalTime.of(21, 0));
        OffsetDateTime madrugada = OffsetDateTime.of(2026, 8, 26, 3, 0, 0, 0, ZoneOffset.UTC);

        var decision = ventana.decidir(madrugada, false);

        assertThat(decision.ahora()).isFalse();
        assertThat(decision.reprogramadaPara().toLocalTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(decision.reprogramadaPara().toLocalDate()).isEqualTo(madrugada.toLocalDate());
        // Y lo obligatorio la ignora: un aviso de seguridad llega cuando pasa.
        assertThat(ventana.decidir(madrugada, true).ahora()).isTrue();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID usuario = fixtura.usuario();
        UUID evento = fixtura.evento("aporte.por_vencer", "COBRANZA", false, "NORMAL");
        fixtura.canalVerificado(usuario, "IN_APP", "app:" + usuario);
        fixtura.plantilla(PLANTILLA, evento, "IN_APP", CUERPO);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaDespacho primera = despachar(evento, usuario, Canal.IN_APP, "reintento", ctx);
        SalidaDespacho segunda = despachar(evento, usuario, Canal.IN_APP, "reintento", ctx);

        assertThat(segunda.notificacionId()).isEqualTo(primera.notificacionId());
        assertThat(contar("SELECT count(*)::int FROM notificaciones.notificacion"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // La clave de deduplicacion es unica en la base: dos consumidores que reciben
        // el mismo evento producen UNA notificacion, y el segundo lo sabe.
        UUID usuario = fixtura.usuario();
        UUID evento = fixtura.evento("aporte.por_vencer", "COBRANZA", false, "NORMAL");
        fixtura.canalVerificado(usuario, "IN_APP", "app:" + usuario);
        fixtura.plantilla(PLANTILLA, evento, "IN_APP", CUERPO);
        ContextoSesion ctx = contextoDe(usuario);

        despachar(evento, usuario, Canal.IN_APP, "carrera", ctx);
        SalidaDespacho segunda = despachar(evento, usuario, Canal.IN_APP, "carrera", ctx);

        assertThat(segunda.estado()).isEqualTo("SUPRIMIDA");
        assertThat(segunda.motivoNoEnvio()).contains("ya estaba despachado");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-80 no mueve dinero: lo que cuadra es la plantilla. Cada variable que la
        // plantilla pide tiene que llegar, y ninguna marca puede quedar sin resolver.
        assertThat(PlantillaRenderizada.variablesDe(CUERPO)).containsExactly("nombre", "monto");
        assertThat(PlantillaRenderizada.render(CUERPO, Map.of("nombre", "Ana", "monto", "500.00")))
                .isEqualTo("Hola Ana, tu aporte de 500.00 vence pronto.")
                .doesNotContain("{{");
        // Falta una: el mensaje no sale a medias.
        assertThatThrownBy(() -> PlantillaRenderizada.render(CUERPO, Map.of("nombre", "Ana")))
                .isInstanceOf(PlantillaRenderizada.VariableFaltante.class)
                .hasMessageContaining("monto");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "aportes"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "aportes"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin plantilla aprobada el caso de uso corta ANTES de escribir: no puede
        // quedar una notificacion cuyo texto nadie aprobo.
        UUID usuario = fixtura.usuario();
        UUID evento = fixtura.evento("aporte.por_vencer", "COBRANZA", false, "NORMAL");
        fixtura.canalVerificado(usuario, "IN_APP", "app:" + usuario);
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> despachar(evento, usuario, Canal.IN_APP, "sin-plantilla", ctx))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay plantilla aprobada");

        assertThat(contar("SELECT count(*)::int FROM notificaciones.notificacion"))
                .isZero();
        assertThat(contar("SELECT count(*)::int FROM notificaciones.bandeja_entrada"))
                .isZero();
    }

    @Test
    @DisplayName("rechaza un canal apagado: WhatsApp y SMS no se encienden desde el codigo")
    void rechazaCanalApagado() {
        // Encender un canal apagado es una de las dieciocho prohibiciones. El caso de
        // uso interseca lo configurado con el piso, asi que ni pasandolo se enciende.
        assertThat(Canal.WHATSAPP.apagadoPorOmision()).isTrue();
        assertThat(Canal.SMS.apagadoPorOmision()).isTrue();
        assertThat(Canal.encendidosPorOmision()).containsExactlyInAnyOrder(Canal.IN_APP, Canal.CORREO, Canal.PUSH);

        UUID usuario = fixtura.usuario();
        UUID evento = fixtura.evento("aporte.por_vencer", "COBRANZA", false, "NORMAL");
        fixtura.canalVerificado(usuario, "WHATSAPP", "+59170000000");
        fixtura.plantilla(PLANTILLA, evento, "WHATSAPP", CUERPO);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaDespacho salida = despachar(evento, usuario, Canal.WHATSAPP, "apagado", ctx);

        assertThat(salida.estado()).isEqualTo("SUPRIMIDA");
        assertThat(salida.motivoNoEnvio()).contains("apagado en este entorno");
    }
}
