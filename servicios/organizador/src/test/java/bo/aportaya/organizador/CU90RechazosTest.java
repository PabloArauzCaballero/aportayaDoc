package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU90PostularOrganizador.EntradaAprobacion;
import bo.aportaya.organizador.aplicacion.CU90PostularOrganizador.EntradaPostulacion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-90 · las pruebas de RECHAZO, una por restriccion citada. */
class CU90RechazosTest extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID postular(UUID usuario, ContextoSesion ctx) {
        return transaccion
                .execute(t -> postulacionCU.postular(
                        new EntradaPostulacion(
                                "Motivacion",
                                "Experiencia",
                                fixtura.kycAprobado(usuario),
                                new BigDecimal("82.00"),
                                Map.of()),
                        ctx))
                .solicitudId();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La solicitud resuelta no se borra para volver a intentar: el rastro de que
        // alguien postulo y con que resultado es lo que permite ver un patron.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        UUID solicitudId = postular(usuario, ctx);
        ContextoSesion revisor = contextoDe(fixtura.usuario());
        transaccion.execute(t -> postulacionCU.aprobar(new EntradaAprobacion(solicitudId, Map.of()), revisor));

        // La resolucion queda con su fecha y su revisor, no se puede dejar en blanco.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM organizador.solicitud_organizador
                         WHERE id = ? AND estado = 'APROBADA' AND revisada_por IS NOT NULL
                           AND fecha_resolucion IS NOT NULL
                        """,
                        solicitudId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Todo cambio relevante emite su evento en la MISMA transaccion. Un alta de
        // organizador que nadie publica deja a los otros servicios sin enterarse de
        // que hay alguien nuevo administrando plata.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        UUID solicitudId = postular(usuario, ctx);
        ContextoSesion revisor = contextoDe(fixtura.usuario());
        var habilitacion =
                transaccion.execute(t -> postulacionCU.aprobar(new EntradaAprobacion(solicitudId, Map.of()), revisor));

        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "organizador.postulacion_aprobada",
                        habilitacion.organizadorId()))
                .isEqualTo(1);
        // Y la base no admite un evento con estado inventado: si pudiera, uno nunca
        // publicado pareceria publicado.
        assertThat(
                        rechazaLaBase(
                                "UPDATE organizador.evento_dominio SET estado = 'INVENTADO' WHERE tipo = 'organizador.postulacion_aprobada'"))
                .contains("ck_organizador_evtdom_estado");
    }

    @Test
    @DisplayName("rechaza por R-ORG-01")
    void rechazaRORG01() {
        // Una postulacion PENDIENTE por usuario. Varias abiertas permiten que dos
        // revisores lleguen a conclusiones distintas sobre la misma persona.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        postular(usuario, ctx);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.solicitud_organizador
                            (id, usuario_id, motivacion, experiencia_declarada,
                             puntaje_reputacion_al_solicitar, estado, fecha_solicitud)
                        VALUES (gen_random_uuid(), '%s', 'otra', 'otra', 90, 'PENDIENTE', now())
                        """
                                .formatted(usuario)))
                .contains("uq_solicitud_organizador_pendiente");
    }

    @Test
    @DisplayName("rechaza por R-ORG-02")
    void rechazaRORG02() {
        // Sin contrato firmado vigente no se crean grupos. El organizador recien
        // aprobado todavia no puede hacer nada con plata ajena.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        UUID solicitudId = postular(usuario, ctx);
        ContextoSesion revisor = contextoDe(fixtura.usuario());
        var habilitacion =
                transaccion.execute(t -> postulacionCU.aprobar(new EntradaAprobacion(solicitudId, Map.of()), revisor));

        boolean puede = Boolean.TRUE.equals(
                transaccion.execute(t -> contratoCU.puedeCrearGrupos(habilitacion.organizadorId(), ctx)));
        assertThat(puede).isFalse();
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Nadie se habilita a si mismo. Autoaprobarse el acceso al fondo de un grupo
        // es exactamente el control que esta regla existe para impedir.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        UUID solicitudId = postular(usuario, ctx);

        assertThatThrownBy(() -> transaccion.execute(
                        t -> postulacionCU.aprobar(new EntradaAprobacion(solicitudId, Map.of()), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser quien la presento");
        assertThat(contar("SELECT count(*)::int FROM organizador.organizador WHERE usuario_id = ?", usuario))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        // Sin diligencia reforzada no se administra plata ajena, y se comprueba ANTES
        // de escribir: una postulacion sin KYC que llega a la tabla ya puede ser
        // aprobada por distraccion.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(t -> postulacionCU.postular(
                        new EntradaPostulacion("Motivacion", "Experiencia", null, new BigDecimal("82.00"), Map.of()),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("diligencia reforzada");
        assertThat(contar("SELECT count(*)::int FROM organizador.solicitud_organizador WHERE usuario_id = ?", usuario))
                .isZero();

        // Y la base exige que el KYC referenciado exista de verdad: un identificador
        // inventado no pasa.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.solicitud_organizador
                            (id, usuario_id, motivacion, experiencia_declarada, kyc_reforzado_id,
                             puntaje_reputacion_al_solicitar, estado, fecha_solicitud)
                        VALUES (gen_random_uuid(), '%s', 'sin kyc', 'sin kyc', gen_random_uuid(), 90,
                                'PENDIENTE', now())
                        """
                                .formatted(usuario)))
                .contains("fk_solicitud_organizador_kyc_reforzado_id");
    }
}
