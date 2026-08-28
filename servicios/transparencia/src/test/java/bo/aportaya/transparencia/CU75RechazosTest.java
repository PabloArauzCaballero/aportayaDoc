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

/** CU-75 · Lo que la base y el caso de uso rechazan. */
class CU75RechazosTest extends BaseDeTransparencia {

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
        todo.put("indicePuntualidad", "0.96");
        return todo;
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La foto de la que sale el certificado no se edita: si se pudiera cambiar el
        // puntaje de la foto, el hash del certificado dejaria de significar nada. La
        // boveda no la declara append-only (HUECO H-7), asi que quien la sostiene es la
        // unicidad del certificado por foto: emitir otro sobre la misma es imposible.
        Caso c = caso();
        transaccion.execute(t -> certificadoCU.emitir(
                new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), Set.of("puntaje"), 90, true),
                c.ctx()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.certificado_reputacion
                            (usuario_id, snapshot_id, codigo_verificacion, hash_contenido, firma_digital,
                             url_publica, expira_en)
                        VALUES (?, ?, 'AY-TEST-TEST-TEST-TEST', repeat('a', 64), 'firma', 'https://x', now() + interval '1 day')
                        """,
                        c.usuario(),
                        c.snapshot()))
                .contains("uq_certificado_reputacion_snapshot_id");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // El aviso al titular sale en la misma transaccion, con la URL y el vencimiento:
        // un certificado emitido del que el titular se entera despues no le sirve para
        // avisar si no fue el.
        Caso c = caso();
        var emitido = transaccion.execute(t -> certificadoCU.emitir(
                new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), Set.of("puntaje"), 90, true),
                c.ctx()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.certificado_emitido' AND agregado_id = ?
                           AND payload->>'expiraEn' IS NOT NULL AND payload->>'camposIncluidos' = 'puntaje'
                        """,
                        emitido.certificadoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-REP-02")
    void rechazaRREP02() {
        // Un certificado sale de UNA foto, y de una foto sale UN certificado. Dos
        // codigos para el mismo documento harian que revocar uno dejara el otro vivo.
        Caso c = caso();
        var entrada = new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), Set.of("puntaje"), 90, true);

        var primero = transaccion.execute(t -> certificadoCU.emitir(entrada, c.ctx()));
        var segundo = transaccion.execute(t -> certificadoCU.emitir(entrada, c.ctx()));

        assertThat(segundo.codigoVerificacion()).isEqualTo(primero.codigoVerificacion());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.certificado_reputacion WHERE snapshot_id = ?",
                        c.snapshot()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-REP-03")
    void rechazaRREP03() {
        // Lo que se muestra es lo que se firma. Un contenido alterado en un caracter
        // deja de verificar: es lo unico que hace que el tercero pueda confiar sin
        // preguntarnos.
        Caso c = caso();
        var emitido = transaccion.execute(t -> certificadoCU.emitir(
                new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), Set.of("puntaje", "nivel"), 90, true),
                c.ctx()));

        var alterado = new LinkedHashMap<>(emitido.contenido());
        alterado.put("nivel", "REFERENTE");
        var falso = transaccion.execute(
                t -> certificadoCU.verificarPublico(emitido.codigoVerificacion(), alterado, contextoDeSistema()));

        assertThat(falso.valido()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE codigo = ? AND resultado = 'NO_COINCIDE'",
                        emitido.codigoVerificacion()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // Todo acceso al dato sensible queda registrado. Cada consulta publica deja su
        // fila y su contador: es como se detecta un codigo probado a fuerza bruta.
        Caso c = caso();
        var emitido = transaccion.execute(t -> certificadoCU.emitir(
                new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), Set.of("puntaje"), 90, true),
                c.ctx()));

        transaccion.execute(
                t -> certificadoCU.verificarPublico(emitido.codigoVerificacion(), null, contextoDeSistema()));
        transaccion.execute(t -> certificadoCU.verificarPublico("AY-AAAA-BBBB-CCCC-DDDD", null, contextoDeSistema()));

        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE codigo = ?",
                        emitido.codigoVerificacion()))
                .isEqualTo(1);
        // El intento contra un codigo inexistente tambien se registra: es justamente el
        // que hay que poder ver repetido.
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE codigo = 'AY-AAAA-BBBB-CCCC-DDDD' AND resultado = 'SIN_DATOS'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // Solo el titular emite el suyo. Emitir el certificado de otro seria publicar su
        // reputacion sin que se entere.
        Caso c = caso();
        UUID otro = fixtura.usuario();

        assertThatThrownBy(() -> transaccion.execute(t -> certificadoCU.emitir(
                        new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), Set.of("puntaje"), 90, true),
                        contextoDe(otro))))
                .hasMessageContaining("Solo el titular");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.certificado_reputacion WHERE usuario_id = ?",
                        c.usuario()))
                .isZero();

        // Y lo que no se pidio no sale, ni siquiera para el titular: el certificado no
        // filtra por descuido lo que la persona decidio no mostrar.
        var emitido = transaccion.execute(t -> certificadoCU.emitir(
                new EntradaCertificado(c.usuario(), c.snapshot(), disponibles(), Set.of("antiguedad"), 90, true),
                c.ctx()));
        assertThat(emitido.contenido()).containsOnlyKeys("antiguedad");
    }
}
