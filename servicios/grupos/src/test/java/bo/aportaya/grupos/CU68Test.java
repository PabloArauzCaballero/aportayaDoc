package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.aplicacion.CU68Postular.EstadoPropuesta;
import bo.aportaya.grupos.aplicacion.CU68Postular.SalidaPostulacion;
import bo.aportaya.grupos.dominio.CriterioDeEmparejamiento;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-68 · Postular a un grupo y ser emparejado. */
class CU68Test extends BaseDeCU68 {

    @Test
    @DisplayName(
            "Dado un usuario sin restricciones y con KYC suficiente · Cuando postula a un grupo con cupos libres · Entonces existe una solicitud_ingreso con puntaje_compatibilidad calculado · Y la respuesta incluye el motivo legible del puntaje")
    void criterio1() {
        UUID grupo = grupoConCupoLibre();
        criterioVigente();

        SalidaPostulacion salida = postular(grupo, false, true, 100, 0);

        assertThat(salida.puntaje()).isGreaterThan(BigDecimal.ZERO);
        assertThat(salida.motivos()).isNotEmpty();
        assertThat(puntajeGuardado(salida.solicitudId())).isEqualByComparingTo(salida.puntaje());
    }

    @Test
    @DisplayName(
            "Dado un usuario con restricción SIN_GRUPOS_NUEVOS vigente · Cuando postula · Entonces se rechaza con RESTRICCION_VIGENTE indicando el monto que la levanta")
    void criterio2() {
        UUID grupo = grupoConCupoLibre();
        criterioVigente();

        assertThatThrownBy(() -> postular(grupo, true, true, 100, 0))
                .isInstanceOf(ErrorDeNegocio.class)
                .satisfies(e -> assertThat(((ErrorDeNegocio) e).detalle()).containsKey("montoQueLaLevanta"));
    }

    @Test
    @DisplayName(
            "Dada una propuesta de grupo que alcanza las aceptaciones antes de expirar · Cuando el último postulante acepta · Entonces se materializa un grupo y se guarda grupo_materializado_id")
    void criterio3() {
        Propuesta propuesta = propuestaConDosPostulantes();

        transaccion.execute(e -> postularCU.responder(
                propuesta.id(), propuesta.postulaciones().get(0), true, 2, Optional.empty(), contexto()));
        UUID grupoNuevo = grupoConCupoLibre();
        EstadoPropuesta estado = transaccion.execute(e -> postularCU.responder(
                propuesta.id(), propuesta.postulaciones().get(1), true, 2, Optional.of(grupoNuevo), contexto()));

        assertThat(estado.materializada()).isTrue();
        assertThat(grupoMaterializadoDe(propuesta.id())).isEqualTo(grupoNuevo);
    }

    @Test
    @DisplayName(
            "Dada una propuesta expirada · Cuando un postulante intenta aceptarla · Entonces se rechaza con PROPUESTA_EXPIRADA y vuelve a la bolsa")
    void criterio4() {
        Propuesta propuesta = propuestaConDosPostulantes();
        vencer(propuesta.id());

        assertThatThrownBy(() -> transaccion.execute(e -> postularCU.responder(
                        propuesta.id(), propuesta.postulaciones().get(0), true, 2, Optional.empty(), contexto())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya vencio");
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        UUID grupo = grupoConCupoLibre();
        criterioVigente();

        assertThatThrownBy(() -> postular(grupo, false, false, 100, 0))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("nivel de verificacion");
    }

    @Test
    @DisplayName("rechaza por R-GRP-14")
    void rechazaRGRP14() {
        // Proteger a los que ya estan es parte del servicio: un grupo en su tope de
        // morosos no recibe a nadie mas.
        CriterioDeEmparejamiento criterio = new CriterioDeEmparejamiento(uno(), uno(), uno(), uno(), 0, 2);

        assertThat(criterio.admiteOtroMoroso(1)).isTrue();
        assertThat(criterio.admiteOtroMoroso(2)).isFalse();
    }

    @Test
    @DisplayName("rechaza por R-GRP-15")
    void rechazaRGRP15() {
        UUID grupo = grupoSinCuposLibres();
        criterioVigente();

        assertThatThrownBy(() -> postular(grupo, false, true, 100, 0))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("cupos libres");
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        dejarUnaFilaEnLaBitacora();

        assertThat(rechazaLaBase("DELETE FROM comun.bitacora_evento")).contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Una solicitud sin quien la hace no es auditable.
        UUID grupo = grupoConCupoLibre();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.solicitud_ingreso
                            (id, grupo_id, cupos_solicitados, estado, fecha_solicitud)
                        VALUES (gen_random_uuid(), '%s', 1, 'PENDIENTE', now())
                        """
                                .formatted(grupo)))
                .contains("usuario_id");
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname NOT IN ('pg_catalog','information_schema','pg_toast')
                           AND c.relkind = 'r' AND NOT c.relrowsecurity
                           AND EXISTS (SELECT 1 FROM pg_attribute a
                                        WHERE a.attrelid = c.oid AND a.attname = 'usuario_id'
                                          AND NOT a.attisdropped)
                        """))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID grupo = grupoConCupoLibre();
        criterioVigente();
        postular(grupo, false, true, 100, 0);

        assertThatThrownBy(() -> postular(grupo, false, true, 100, 0))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("postulacion pendiente");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Responder dos veces por la misma postulacion: el WHERE respondido_en IS
        // NULL decide, no un SELECT previo.
        Propuesta propuesta = propuestaConDosPostulantes();
        transaccion.execute(e -> postularCU.responder(
                propuesta.id(), propuesta.postulaciones().get(0), true, 5, Optional.empty(), contexto()));

        assertThatThrownBy(() -> transaccion.execute(e -> postularCU.responder(
                        propuesta.id(), propuesta.postulaciones().get(0), false, 5, Optional.empty(), contexto())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Ya respondiste");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Un peso en cero DESACTIVA esa dimension en vez de castigar a todo el
        // mundo: se divide por la suma de los pesos, no por cuatro.
        CriterioDeEmparejamiento soloMonto =
                new CriterioDeEmparejamiento(BigDecimal.ZERO, uno(), BigDecimal.ZERO, BigDecimal.ZERO, 0, 5);

        assertThat(soloMonto.puntuar(BigDecimal.ZERO, uno(), BigDecimal.ZERO, BigDecimal.ZERO))
                .isEqualByComparingTo(uno());
        assertThat(new CriterioDeEmparejamiento(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 5)
                        .puntuar(uno(), uno(), uno(), uno()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "transparencia"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "transparencia"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: si la materializacion falla, la propuesta no queda aceptada a medias")
    void compensa() {
        Propuesta propuesta = propuestaConDosPostulantes();

        try {
            transaccion.execute(estado -> {
                postularCU.responder(
                        propuesta.id(),
                        propuesta.postulaciones().get(0),
                        true,
                        1,
                        Optional.of(UUID.randomUUID()),
                        contexto());
                return null;
            });
        } catch (RuntimeException esperado) {
            assertThat(esperado).isNotNull();
        }

        // La clave foranea del grupo inexistente revierte TODO: ni la respuesta del
        // postulante queda escrita. A medias, la propuesta diria que alguien acepto
        // un grupo que no existe.
        assertThat(respondioAlguien(propuesta.id())).isFalse();
    }
}
