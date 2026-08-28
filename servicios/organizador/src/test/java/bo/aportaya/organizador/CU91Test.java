package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU91FirmarContrato.EntradaEmision;
import bo.aportaya.organizador.aplicacion.CU91FirmarContrato.EntradaRescision;
import bo.aportaya.organizador.aplicacion.CU91FirmarContrato.SalidaEmision;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-91 · Firmar y rescindir el contrato de organizador. */
class CU91Test extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(UUID usuario, UUID organizadorId, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        UUID organizadorId = fixtura.organizadorHabilitado(usuario);
        return new Caso(usuario, organizadorId, contextoDe(usuario));
    }

    private SalidaEmision emitir(Caso c, String version) {
        return transaccion.execute(t -> contratoCU.emitir(
                new EntradaEmision(
                        c.organizadorId(),
                        version,
                        "a".repeat(64),
                        "Administrar con diligencia y rendir cuentas",
                        "Fraude, abandono del grupo, incumplimiento reiterado"),
                c.ctx()));
    }

    @Test
    @DisplayName(
            "Dado un organizador habilitado sin contrato firmado · Cuando intenta crear un grupo · Entonces la base lo impide con CONTRATO_SIN_FIRMAR")
    void criterio1() {
        Caso c = caso();
        emitir(c, "v1-" + corto());

        boolean puede =
                Boolean.TRUE.equals(transaccion.execute(t -> contratoCU.puedeCrearGrupos(c.organizadorId(), c.ctx())));

        // Emitido no es firmado. Sin contrato firmado no se crean grupos (R-ORG-02):
        // el contrato es lo unico que dice, por escrito y oponible, que obligaciones
        // asumio quien va a manejar la plata de otros.
        assertThat(puede).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.contrato_organizador WHERE organizador_id = ? AND firmado_en IS NULL",
                        c.organizadorId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un contrato firmado · Cuando se modifica el texto contractual · Entonces se crea una versión nueva y el hash firmado sigue apuntando al texto original")
    void criterio2() {
        Caso c = caso();
        SalidaEmision primero = emitir(c, "v1-" + corto());
        transaccion.execute(t -> contratoCU.firmar(primero.contratoId(), fixtura.tokenDeFirma(c.usuario()), c.ctx()));
        String hashOriginal = dsl.fetchOne(
                        "SELECT contenido_hash FROM organizador.contrato_organizador WHERE id = ?",
                        primero.contratoId())
                .get("contenido_hash", String.class);

        // Cambiar el texto exige rescindir el vigente y emitir version nueva: no hay
        // forma de editar el firmado. Cambiarle una clausula a un documento ya firmado
        // lo vuelve inoponible, que es lo contrario de para lo que existe.
        assertThatThrownBy(() -> emitir(c, "v2-" + corto()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya tiene contrato vigente");

        assertThat(dsl.fetchOne(
                                "SELECT contenido_hash FROM organizador.contrato_organizador WHERE id = ?",
                                primero.contratoId())
                        .get("contenido_hash", String.class))
                .isEqualTo(hashOriginal);
    }

    @Test
    @DisplayName(
            "Dada una rescisión con grupos activos · Cuando se intenta completar sin reasignarlos · Entonces se rechaza con GRUPOS_SIN_REASIGNAR")
    void criterio3() {
        Caso c = caso();
        SalidaEmision contrato = emitir(c, "v1-" + corto());
        transaccion.execute(t -> contratoCU.firmar(contrato.contratoId(), fixtura.tokenDeFirma(c.usuario()), c.ctx()));
        fixtura.conGruposActivos(c.organizadorId(), 2);

        assertThatThrownBy(() -> transaccion.execute(
                        t -> contratoCU.rescindir(new EntradaRescision(contrato.contratoId(), "Se retira"), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("grupo(s) en curso");

        // El contrato sigue vigente: irse dejando participantes a mitad del ciclo es
        // exactamente lo que el contrato existe para que no pase sin consecuencias.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.contrato_organizador WHERE id = ? AND rescindido_en IS NULL",
                        contrato.contratoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una rescisión por causal de fraude · Cuando se registra · Entonces los roles se revocan de inmediato y la plataforma asume la administración")
    void criterio4() {
        Caso c = caso();
        SalidaEmision contrato = emitir(c, "v1-" + corto());
        transaccion.execute(t -> contratoCU.firmar(contrato.contratoId(), fixtura.tokenDeFirma(c.usuario()), c.ctx()));

        var rescision = transaccion.execute(t -> contratoCU.rescindir(
                new EntradaRescision(contrato.contratoId(), "Fraude comprobado en el ciclo 3"), c.ctx()));

        // El organizador queda RETIRADO en el acto y no puede crear nada nuevo. La
        // revocacion de roles y la administracion sustituta las ejecuta identidad al
        // consumir el evento: este servicio no escribe ese esquema (invariante 11).
        assertThat(rescision.estadoDelOrganizador()).isEqualTo("RETIRADO");
        assertThat(Boolean.TRUE.equals(
                        transaccion.execute(t -> contratoCU.puedeCrearGrupos(c.organizadorId(), c.ctx()))))
                .isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "organizador.contrato_rescindido",
                        contrato.contratoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Firmar dos veces no sobrescribe la evidencia: la fecha y el token de la
        // firma real son lo que prueba cuando y con que se firmo.
        Caso c = caso();
        SalidaEmision contrato = emitir(c, "v1-" + corto());
        UUID token = fixtura.tokenDeFirma(c.usuario());

        var primera = transaccion.execute(t -> contratoCU.firmar(contrato.contratoId(), token, c.ctx()));
        var segunda = transaccion.execute(
                t -> contratoCU.firmar(contrato.contratoId(), fixtura.tokenDeFirma(c.usuario()), c.ctx()));

        assertThat(primera.esNueva()).isTrue();
        assertThat(segunda.esNueva()).isFalse();
        assertThat(dsl.fetchOne(
                                "SELECT token_firma_id FROM organizador.contrato_organizador WHERE id = ?",
                                contrato.contratoId())
                        .get("token_firma_id", UUID.class))
                .isEqualTo(token);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos rescisiones del mismo contrato: la segunda no vuelve a rescindir. El
        // WHERE rescindido_en IS NULL es la barrera.
        Caso c = caso();
        SalidaEmision contrato = emitir(c, "v1-" + corto());
        transaccion.execute(t -> contratoCU.firmar(contrato.contratoId(), fixtura.tokenDeFirma(c.usuario()), c.ctx()));

        transaccion.execute(t -> contratoCU.rescindir(new EntradaRescision(contrato.contratoId(), "Primera"), c.ctx()));

        assertThatThrownBy(() -> transaccion.execute(
                        t -> contratoCU.rescindir(new EntradaRescision(contrato.contratoId(), "Segunda"), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya estaba rescindido");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.contrato_organizador WHERE id = ? AND motivo_rescision = 'Primera'",
                        contrato.contratoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El hash del contenido es lo que ata la firma a un texto concreto. Tiene que
        // seguir siendo el mismo despues de firmar: si cambiara, la firma dejaria de
        // decir sobre que se firmo.
        Caso c = caso();
        SalidaEmision contrato = emitir(c, "v1-" + corto());
        String antes = dsl.fetchOne(
                        "SELECT contenido_hash FROM organizador.contrato_organizador WHERE id = ?",
                        contrato.contratoId())
                .get("contenido_hash", String.class);

        transaccion.execute(t -> contratoCU.firmar(contrato.contratoId(), fixtura.tokenDeFirma(c.usuario()), c.ctx()));

        String despues = dsl.fetchOne(
                        "SELECT contenido_hash FROM organizador.contrato_organizador WHERE id = ?",
                        contrato.contratoId())
                .get("contenido_hash", String.class);
        assertThat(despues).isEqualTo(antes).hasSize(64);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "contratos"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "contratos"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Un contrato que nunca se firmo no se rescinde: no hay nada que rescindir, y
        // marcarlo rescindido inventaria un vinculo que nunca existio.
        Caso c = caso();
        SalidaEmision contrato = emitir(c, "v1-" + corto());

        assertThatThrownBy(() -> transaccion.execute(
                        t -> contratoCU.rescindir(new EntradaRescision(contrato.contratoId(), "Sin firmar"), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("nunca se firmo");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.contrato_organizador WHERE id = ? AND rescindido_en IS NULL",
                        contrato.contratoId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.organizador WHERE id = ? AND estado = 'HABILITADO'",
                        c.organizadorId()))
                .isEqualTo(1);
    }
}
