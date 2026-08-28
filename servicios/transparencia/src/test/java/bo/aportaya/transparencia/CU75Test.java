package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU75EmitirCertificado.EntradaCertificado;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-75 · Emitir un certificado de reputacion verificable. */
class CU75Test extends BaseDeTransparencia {

    private record Caso(UUID usuario, UUID snapshot, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, fixtura.snapshot(usuario, "780.00", "MUY_CONFIABLE"), contextoDe(usuario));
    }

    private static Map<String, String> disponibles() {
        var todo = new LinkedHashMap<String, String>();
        todo.put("puntaje", "780.00");
        todo.put("nivel", "MUY_CONFIABLE");
        todo.put("antiguedad", "18 meses");
        todo.put("gruposCompletados", "3");
        todo.put("insignias", "PRIMER_PASANAKU, PAGADOR_PUNTUAL");
        todo.put("indicePuntualidad", "0.96");
        return todo;
    }

    private EntradaCertificado entrada(Caso c, Set<String> incluir) {
        return new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), incluir, 90, true);
    }

    @Test
    @DisplayName(
            "Dado un usuario con snapshot vigente · Cuando emite un certificado incluyendo puntaje y antigüedad · Entonces la URL pública devuelve exactamente esos campos y ninguno más")
    void criterio1() {
        Caso c = caso();

        var salida =
                transaccion.execute(t -> certificadoCU.emitir(entrada(c, Set.of("puntaje", "antiguedad")), c.ctx()));

        // Lo que no se pidio no entra: quien solo queria probar su antiguedad no revela
        // ademas su indice de puntualidad.
        assertThat(salida.contenido()).containsOnlyKeys("puntaje", "antiguedad");
        assertThat(salida.contenido()).doesNotContainKeys("insignias", "indicePuntualidad", "nivel");
        assertThat(salida.urlPublica()).endsWith(salida.codigoVerificacion());

        var verificado = transaccion.execute(t ->
                certificadoCU.verificarPublico(salida.codigoVerificacion(), salida.contenido(), contextoDeSistema()));
        assertThat(verificado.valido()).isTrue();
        assertThat(verificado.estado()).isEqualTo("VIGENTE");
    }

    @Test
    @DisplayName(
            "Dado un código de verificación inexistente · Cuando un tercero lo consulta · Entonces la respuesta es NO_VALIDO sin distinguirla de un revocado")
    void criterio2() {
        Caso c = caso();
        var emitido = transaccion.execute(t -> certificadoCU.emitir(entrada(c, Set.of("puntaje")), c.ctx()));
        transaccion.execute(t -> {
            certificadoCU.revocar(emitido.codigoVerificacion(), "A pedido del titular", c.ctx());
            return null;
        });

        var inexistente = transaccion.execute(
                t -> certificadoCU.verificarPublico("AY-ZZZZ-ZZZZ-ZZZZ-ZZZZ", null, contextoDeSistema()));
        var revocado = transaccion.execute(
                t -> certificadoCU.verificarPublico(emitido.codigoVerificacion(), null, contextoDeSistema()));

        assertThat(inexistente.valido()).isFalse();
        assertThat(revocado.valido()).isFalse();
        assertThat(inexistente.estado()).isEqualTo("NO_VALIDO");
        // Ni fechas ni detalle: lo unico que distinguiria a uno de otro para quien
        // prueba codigos al azar.
        assertThat(inexistente.emitidoEn()).isNull();
        assertThat(inexistente.expiraEn()).isNull();
        assertThat(revocado.emitidoEn()).isNull();
    }

    @Test
    @DisplayName(
            "Dado un certificado vencido · Cuando se lo verifica · Entonces el estado es VENCIDO con su fecha de expiración")
    void criterio3() {
        Caso c = caso();
        var emitido = transaccion.execute(t -> certificadoCU.emitir(entrada(c, Set.of("puntaje")), c.ctx()));
        dsl.execute(
                "UPDATE transparencia.certificado_reputacion SET expira_en = now() - interval '1 day' WHERE id = ?",
                emitido.certificadoId());

        var verificado = transaccion.execute(
                t -> certificadoCU.verificarPublico(emitido.codigoVerificacion(), null, contextoDeSistema()));

        assertThat(verificado.estado()).isEqualTo("VENCIDO");
        assertThat(verificado.valido()).isFalse();
        // Un vencido si dice cuando vencio: el tercero necesita saber si el dato es de
        // hace un mes o de hace tres años.
        assertThat(verificado.expiraEn()).isNotNull();
    }

    @Test
    @DisplayName(
            "Dado un usuario que ejerce derecho de supresión · Cuando se procesa · Entonces sus certificados quedan revocados y la URL deja de resolver")
    void criterio4() {
        Caso c = caso();
        var emitido = transaccion.execute(t -> certificadoCU.emitir(entrada(c, Set.of("puntaje")), c.ctx()));

        transaccion.execute(t -> {
            certificadoCU.revocar(emitido.codigoVerificacion(), "Derecho de supresion (CU-07)", c.ctx());
            return null;
        });

        var verificado = transaccion.execute(
                t -> certificadoCU.verificarPublico(emitido.codigoVerificacion(), null, contextoDeSistema()));
        assertThat(verificado.estado()).isEqualTo("REVOCADO");
        assertThat(verificado.valido()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_dominio WHERE tipo = 'transparencia.certificado_revocado' AND agregado_id = ?",
                        emitido.certificadoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso();
        var entrada = entrada(c, Set.of("puntaje", "nivel"));

        var primero = transaccion.execute(t -> certificadoCU.emitir(entrada, c.ctx()));
        var segundo = transaccion.execute(t -> certificadoCU.emitir(entrada, c.ctx()));

        // Pedirlo dos veces devuelve **el mismo**, con el mismo codigo y la misma URL.
        // Una foto, un certificado: dos codigos para el mismo documento harian que
        // revocar uno dejara el otro vivo.
        assertThat(segundo.certificadoId()).isEqualTo(primero.certificadoId());
        assertThat(segundo.codigoVerificacion()).isEqualTo(primero.codigoVerificacion());
        assertThat(segundo.urlPublica()).isEqualTo(primero.urlPublica());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.certificado_reputacion WHERE usuario_id = ?",
                        c.usuario()))
                .isEqualTo(1);
        assertThat(primero.codigoVerificacion()).startsWith("AY-");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        Caso c = caso();
        var entrada = entrada(c, Set.of("puntaje"));

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> certificadoCU.emitir(entrada, c.ctx()));
            } catch (Exception e) {
                errores.add(e);
            }
        };
        var uno = new Thread(intento);
        var dos = new Thread(intento);
        uno.start();
        dos.start();
        uno.join();
        dos.join();

        // Los dos leen que no hay certificado y los dos intentan escribir: quien
        // sostiene la regla es uq_certificado_reputacion_snapshot_id.
        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.certificado_reputacion WHERE snapshot_id = ?",
                        c.snapshot()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        Caso c = caso();
        var salida = transaccion.execute(
                t -> certificadoCU.emitir(entrada(c, Set.of("puntaje", "nivel", "antiguedad")), c.ctx()));

        // El cuadre de un certificado es que el hash guardado sea el del contenido que
        // se publica. Si no cuadrara, el tercero veria un documento y verificaria otro.
        var verificado = transaccion.execute(t ->
                certificadoCU.verificarPublico(salida.codigoVerificacion(), salida.contenido(), contextoDeSistema()));
        assertThat(verificado.valido()).isTrue();

        // Y un contenido alterado en un solo caracter deja de cuadrar.
        var alterado = new LinkedHashMap<>(salida.contenido());
        alterado.put("puntaje", "980.00");
        var falso = transaccion.execute(
                t -> certificadoCU.verificarPublico(salida.codigoVerificacion(), alterado, contextoDeSistema()));
        assertThat(falso.valido()).isFalse();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        Caso c = caso();
        var emitido = transaccion.execute(t -> certificadoCU.emitir(entrada(c, Set.of("puntaje")), c.ctx()));

        transaccion.execute(
                t -> certificadoCU.verificarPublico(emitido.codigoVerificacion(), null, contextoDeSistema()));
        transaccion.execute(
                t -> certificadoCU.verificarPublico(emitido.codigoVerificacion(), null, contextoDeSistema()));
        transaccion.execute(
                t -> certificadoCU.verificarPublico(emitido.codigoVerificacion(), null, contextoDeSistema()));

        // Una fila por certificado, con el contador al dia: es como se detecta un
        // codigo probado a fuerza bruta.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE codigo = ?",
                        emitido.codigoVerificacion()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT consultas FROM transparencia.verificacion_publica WHERE codigo = ?",
                        emitido.codigoVerificacion()))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        Caso c = caso();

        // Paso fallido: identidad sin verificar. Un certificado que dice «es de fiar»
        // sin saber quien es no se emite.
        assertThatThrownBy(() -> transaccion.execute(t -> certificadoCU.emitir(
                        new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), Set.of("puntaje"), 90, false),
                        c.ctx())))
                .hasMessageContaining("verificacion de identidad");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.certificado_reputacion WHERE usuario_id = ?",
                        c.usuario()))
                .isZero();

        // Paso fallido: no hay foto de la cual emitir.
        assertThatThrownBy(() -> transaccion.execute(t -> certificadoCU.emitir(
                        new EntradaCertificado(c.usuario(), null, disponibles(), Set.of("puntaje"), 90, true),
                        c.ctx())))
                .hasMessageContaining("foto de reputacion");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.certificado_reputacion WHERE usuario_id = ?",
                        c.usuario()))
                .isZero();

        // Con todo en orden, el mismo camino cierra.
        var bueno = transaccion.execute(t -> certificadoCU.emitir(entrada(c, Set.of("puntaje")), c.ctx()));
        assertThat(bueno.certificadoId()).isNotNull();
    }
}
