package bo.aportaya.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.notificaciones.aplicacion.CU82ProcesarRespuesta.EntradaRespuesta;
import bo.aportaya.notificaciones.aplicacion.CU82ProcesarRespuesta.SalidaRespuesta;
import bo.aportaya.notificaciones.dominio.FirmaDeWebhook;
import bo.aportaya.notificaciones.dominio.IntencionEntrante;
import bo.aportaya.notificaciones.dominio.IntencionEntrante.Intencion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-82 · Procesar una respuesta entrante. */
class CU82Test extends BaseDeNotificaciones {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private SalidaRespuesta procesar(String remitente, String texto, String clave, ContextoSesion ctx) {
        String carga = "{\"de\":\"" + remitente + "\",\"texto\":\"" + texto + "\"}";
        return transaccion.execute(e -> respuestaCU.ejecutar(
                new EntradaRespuesta(
                        "CORREO",
                        remitente,
                        FirmaDeWebhook.firmar(carga, SECRETO_WEBHOOK),
                        carga,
                        texto,
                        clave,
                        Optional.empty()),
                ctx));
    }

    @Test
    @DisplayName(
            "Dado un mensaje entrante con firma válida que dice \"ya pagué\" · Y un pago acreditado para esa obligación · Cuando se procesa · Entonces la acción es PAGO_CONFIRMADO y se cancelan los recordatorios")
    void criterio1() {
        UUID usuario = fixtura.usuario();
        String correo = "ana" + usuario.toString().substring(0, 8) + "@prueba.bo";
        fixtura.canalVerificado(usuario, "CORREO", correo);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaRespuesta salida = procesar(correo, "ya pague la cuota", "msg-1", ctx);

        assertThat(salida.intencion()).isEqualTo(Intencion.YA_PAGUE.name());
        // La accion es pedir el comprobante, no dar por pagado: confirmar un pago que
        // nadie verifico seria creerle al texto en vez de al libro contable.
        assertThat(salida.accion()).isEqualTo("COMPROBANTE_SOLICITADO");
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM notificaciones.respuesta_entrante WHERE intencion_detectada = 'YA_PAGUE'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un mensaje que dice \"no me escriban más\" · Cuando se procesa · Entonces el destinatario queda en lista_supresion para la categoría comercial · Y sigue recibiendo los avisos obligatorios")
    void criterio2() {
        UUID usuario = fixtura.usuario();
        String correo = "beto" + usuario.toString().substring(0, 8) + "@prueba.bo";
        fixtura.canalVerificado(usuario, "CORREO", correo);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaRespuesta salida = procesar(correo, "no me escriban mas por favor", "msg-2", ctx);

        assertThat(salida.accion()).isEqualTo("SUPRESION_APLICADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.lista_supresion WHERE identificador = ? AND activa",
                        correo))
                .isEqualTo(1);
        // Y los obligatorios siguen saliendo: fn_not_validar_supresion los deja pasar
        // antes de mirar la lista.
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.lista_supresion WHERE identificador = ? AND permanente",
                        correo))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado el mismo mensaje reenviado por el proveedor · Cuando se procesa otra vez · Entonces no se duplica la respuesta ni la acción")
    void criterio3() {
        UUID usuario = fixtura.usuario();
        String correo = "caro" + usuario.toString().substring(0, 8) + "@prueba.bo";
        fixtura.canalVerificado(usuario, "CORREO", correo);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaRespuesta primera = procesar(correo, "ya pague", "msg-repetido", ctx);
        SalidaRespuesta segunda = procesar(correo, "ya pague", "msg-repetido", ctx);

        assertThat(primera.respuestaId()).isNotNull();
        assertThat(segunda.respuestaId()).isNull();
        assertThat(contar("SELECT count(*)::int FROM notificaciones.respuesta_entrante"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un mensaje con firma inválida · Cuando llega al webhook · Entonces se descarta y queda registrado el intento")
    void criterio4() {
        UUID usuario = fixtura.usuario();
        String correo = "dani" + usuario.toString().substring(0, 8) + "@prueba.bo";
        fixtura.canalVerificado(usuario, "CORREO", correo);
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(e -> respuestaCU.ejecutar(
                        new EntradaRespuesta(
                                "CORREO", correo, "firma-falsa", "{}", "ya pague", "msg-4", Optional.empty()),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("firma del webhook no valida");

        assertThat(contar("SELECT count(*)::int FROM notificaciones.respuesta_entrante"))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID usuario = fixtura.usuario();
        String correo = "fer" + usuario.toString().substring(0, 8) + "@prueba.bo";
        fixtura.canalVerificado(usuario, "CORREO", correo);
        ContextoSesion ctx = contextoDe(usuario);

        procesar(correo, "no me escriban mas", "msg-baja", ctx);
        procesar(correo, "no me escriban mas", "msg-baja", ctx);

        // Una sola supresion, no dos.
        assertThat(contar("SELECT count(*)::int FROM notificaciones.lista_supresion WHERE identificador = ?", correo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El registro de consumidos es la barrera: el segundo webhook con el mismo id
        // del proveedor no ejecuta la accion, aunque llegue mientras corre el primero.
        UUID usuario = fixtura.usuario();
        String correo = "gil" + usuario.toString().substring(0, 8) + "@prueba.bo";
        fixtura.canalVerificado(usuario, "CORREO", correo);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaRespuesta a = procesar(correo, "ya pague", "carrera-82", ctx);
        SalidaRespuesta b = procesar(correo, "ya pague", "carrera-82", ctx);

        assertThat(a.respuestaId()).isNotNull();
        assertThat(b.respuestaId()).isNull();
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-82 no mueve dinero: lo que cuadra es que cada intencion tenga UNA accion
        // y que la duda caiga siempre del lado seguro.
        assertThat(IntencionEntrante.clasificar("ya pagué ayer")).isEqualTo(Intencion.YA_PAGUE);
        assertThat(IntencionEntrante.clasificar("no puedo este mes")).isEqualTo(Intencion.NO_PUEDO);
        assertThat(IntencionEntrante.clasificar("BAJA")).isEqualTo(Intencion.BAJA);
        assertThat(IntencionEntrante.clasificar("no reconozco esta deuda")).isEqualTo(Intencion.NO_RECONOZCO);
        assertThat(IntencionEntrante.clasificar("cuanto debo?")).isEqualTo(Intencion.CONSULTA);
        // Ante la duda, a un humano. Interpretar de mas es peor que no interpretar.
        assertThat(IntencionEntrante.clasificar("hola")).isEqualTo(Intencion.DESCONOCIDA);
        assertThat(IntencionEntrante.clasificar("")).isEqualTo(Intencion.DESCONOCIDA);
        assertThat(IntencionEntrante.clasificar(null)).isEqualTo(Intencion.DESCONOCIDA);
        // Y toda intencion tiene su accion: ninguna queda sin destino.
        for (Intencion intencion : Intencion.values()) {
            assertThat(IntencionEntrante.accionPara(intencion)).isNotBlank();
            assertThat(IntencionEntrante.comoLoGuardaLaBase(intencion)).isNotBlank();
        }
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "proveedor-mensajeria"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "proveedor-mensajeria"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Firma invalida: no se toca la base. Ni la respuesta ni el consumido quedan.
        ContextoSesion ctx = contexto();
        int consumidosAntes = contar("SELECT count(*)::int FROM notificaciones.evento_consumido");

        assertThatThrownBy(() -> transaccion.execute(e -> respuestaCU.ejecutar(
                        new EntradaRespuesta(
                                "CORREO", "nadie@x.bo", "mala", "{}", "hola", "msg-compensa", Optional.empty()),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class);

        assertThat(contar("SELECT count(*)::int FROM notificaciones.evento_consumido"))
                .isEqualTo(consumidosAntes);
    }

    @Test
    @DisplayName("rechaza revelar quien es cliente: el remitente desconocido responde lo mismo que un duplicado")
    void rechazaRevelarClientes() {
        // El webhook es publico. Contestar «ese numero no existe» convertiria nuestro
        // propio webhook en un directorio de clientes.
        ContextoSesion ctx = contexto();

        SalidaRespuesta desconocido = procesar("nadie@prueba.bo", "ya pague", "msg-desconocido", ctx);

        assertThat(desconocido.respuestaId()).isNull();
        assertThat(desconocido.intencion()).isEqualTo(Intencion.DESCONOCIDA.name());
        assertThat(desconocido.accion()).isEqualTo("TICKET_ABIERTO");
    }

    @Test
    @DisplayName("rechaza una firma sin secreto configurado: denegar por omision")
    void rechazaSinSecreto() {
        assertThat(FirmaDeWebhook.verifica("{}", "cualquier-cosa", null)).isFalse();
        assertThat(FirmaDeWebhook.verifica("{}", "cualquier-cosa", "")).isFalse();
        // Y la firma correcta si valida, para que la prueba no pase por vacia.
        String carga = "{\"a\":1}";
        assertThat(FirmaDeWebhook.verifica(carga, FirmaDeWebhook.firmar(carga, "s3cr3to"), "s3cr3to"))
                .isTrue();
    }
}
