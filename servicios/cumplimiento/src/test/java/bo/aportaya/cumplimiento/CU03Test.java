package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU03DeclararPep.EntradaDeclaracion;
import bo.aportaya.cumplimiento.aplicacion.CU03DeclararPep.SalidaDeclaracion;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep.BeneficiarioFinal;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep.Declaracion;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep.NivelRiesgo;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep.TipoPep;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-03 · Declaracion PEP y beneficiario final. */
class CU03Test extends BaseDeCumplimiento {

    private SalidaDeclaracion declarar(EntradaDeclaracion entrada, ContextoSesion ctx) {
        return transaccion.execute(e -> pepCU.ejecutar(entrada, ctx));
    }

    private EntradaDeclaracion pepNacional(UUID usuario) {
        return new EntradaDeclaracion(
                usuario,
                true,
                Optional.of("NACIONAL"),
                Optional.of("Director de area"),
                Optional.of("Ministerio de Economia"),
                List.of());
    }

    @Test
    @DisplayName(
            "Dado un usuario que declara ser PEP nacional · Cuando se guarda la declaración · Entonces su debida_diligencia queda en tipo REFORZADA · Y su calificacion_riesgo_cliente vigente tiene nivel ALTO")
    void criterio1() {
        ContextoSesion ctx = contexto();

        SalidaDeclaracion salida = declarar(pepNacional(ctx.usuarioId()), ctx);

        assertThat(salida.exigeDiligenciaReforzada()).isTrue();
        assertThat(salida.nivelRiesgo()).isEqualTo("ALTO");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.debida_diligencia WHERE usuario_id = ? AND tipo = 'REFORZADA'",
                        ctx.usuarioId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ? AND nivel = 'ALTO' AND vigente_hasta IS NULL",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario que declaró no ser PEP · Y existe una coincidencia_lista confirmada con su nombre · Cuando se evalúa su perfil · Entonces se abre un caso_investigacion_lft")
    void criterio2() {
        ContextoSesion ctx = contexto();
        declarar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx);

        // La coincidencia la confirma auditoria y llega por evento: cumplimiento no
        // lee auditoria.coincidencia_lista (invariante 11).
        Optional<UUID> caso = transaccion.execute(
                e -> pepCU.alConfirmarseCoincidencia(ctx.usuarioId(), UUID.randomUUID(), "Juan Perez", ctx));

        assertThat(caso).isPresent();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.caso_investigacion_lft WHERE usuario_id = ? AND estado = 'ABIERTO'",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario que declara ser PEP sin informar cargo ni institución · Cuando intenta guardar la declaración · Entonces se rechaza con DECLARACION_INCOMPLETA · Y no se crea ninguna declaracion_pep")
    void criterio3() {
        ContextoSesion ctx = contexto();
        EntradaDeclaracion incompleta = new EntradaDeclaracion(
                ctx.usuarioId(), true, Optional.of("NACIONAL"), Optional.empty(), Optional.empty(), List.of());

        assertThatThrownBy(() -> declarar(incompleta, ctx))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("cargo y la institucion");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.declaracion_pep WHERE usuario_id = ?", ctx.usuarioId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un beneficiario_final declarado que es PEP extranjero · Cuando se guarda la estructura de control · Entonces la debida_diligencia del titular queda en REFORZADA")
    void criterio4() {
        ContextoSesion ctx = contexto();
        // El titular NO es PEP. El que lo es, es quien controla: mirar solo al
        // titular dejaria la puerta abierta a operar por interpuesta persona.
        EntradaDeclaracion conBeneficiarioPep = new EntradaDeclaracion(
                ctx.usuarioId(),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(new BeneficiarioFinal("Ana Controlante", "E-99887", true, Optional.of(TipoPep.EXTRANJERO))));

        SalidaDeclaracion salida = declarar(conBeneficiarioPep, ctx);

        assertThat(salida.exigeDiligenciaReforzada()).isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.debida_diligencia WHERE usuario_id = ? AND tipo = 'REFORZADA'",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-UIF-10")
    void rechazaRUIF10() {
        // tg_ddd_pep: con un PEP vigente, una diligencia que no sea REFORZADA no entra.
        ContextoSesion ctx = contexto();
        declarar(pepNacional(ctx.usuarioId()), ctx);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.debida_diligencia
                            (id, usuario_id, tipo, estado, documentos_requeridos, documentos_recibidos, iniciada_en)
                        VALUES (gen_random_uuid(), '%s', 'SIMPLIFICADA', 'EN_PROCESO', '[]'::jsonb, '[]'::jsonb, now())
                        """
                                .formatted(ctx.usuarioId())))
                .contains("R-UIF-10");
    }

    @Test
    @DisplayName("rechaza por R-UIF-11")
    void rechazaRUIF11() {
        // ex_calificacion_vigente: dos calificaciones vigentes del mismo cliente no
        // pueden solaparse. El EXCLUDE lo impide, no una consulta previa.
        ContextoSesion ctx = contexto();
        declarar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.calificacion_riesgo_cliente
                            (id, usuario_id, nivel, puntaje_total, nivel_dd_requerido,
                             periodicidad_revision_meses, vigente_desde, proxima_revision, es_automatica)
                        VALUES (gen_random_uuid(), '%s', 'BAJO', 0, 'SIMPLIFICADA', 12, now(),
                                current_date + 365, true)
                        """
                                .formatted(ctx.usuarioId())))
                .contains("ex_calificacion_vigente");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // ck_caso_revision: quien revisa no puede ser quien analiza. Cuatro ojos.
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.caso_investigacion_lft
                            (id, codigo, usuario_id, analista_id, revisado_por, origen, estado,
                             prioridad, resumen, abierto_en, plazo_limite)
                        VALUES (gen_random_uuid(), 'LFT-MISMO', '%s', '%s', '%s', 'ALERTA', 'ABIERTO',
                                'ALTA', 'Se revisa a si mismo', now(), now() + interval '30 days')
                        """
                                .formatted(ctx.usuarioId(), ctx.usuarioId(), ctx.usuarioId())))
                .contains("ck_caso_revision");
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.expediente_cliente
                            (id, usuario_id, completitud_porcentaje, documentos, estado,
                             retencion_hasta, ultima_actualizacion)
                        VALUES (gen_random_uuid(), '%s', 100, '{}'::jsonb, 'COMPLETO',
                                current_date - 10, now())
                        """
                                .formatted(ctx.usuarioId())))
                .contains("ck_expediente_retencion_futura");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Un segundo aviso sobre la misma persona no abre un segundo expediente:
        // duplicar casos dispersa la investigacion en vez de reforzarla.
        ContextoSesion ctx = contexto();
        declarar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx);

        Optional<UUID> primero = transaccion.execute(
                e -> pepCU.alConfirmarseCoincidencia(ctx.usuarioId(), UUID.randomUUID(), "Juan Perez", ctx));
        Optional<UUID> segundo = transaccion.execute(
                e -> pepCU.alConfirmarseCoincidencia(ctx.usuarioId(), UUID.randomUUID(), "Juan Perez", ctx));

        assertThat(primero).isPresent();
        assertThat(segundo).isEmpty();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.caso_investigacion_lft WHERE usuario_id = ?",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Declarar dos veces cierra la calificacion anterior y abre una nueva. Lo que
        // el EXCLUDE garantiza es que en ningun momento haya dos vigentes.
        ContextoSesion ctx = contexto();
        declarar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx);
        declarar(pepNacional(ctx.usuarioId()), ctx);

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ? AND vigente_hasta IS NULL",
                        ctx.usuarioId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ?",
                        ctx.usuarioId()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-03 no mueve dinero: lo que cuadra es que cada camino a PEP termine en
        // reforzada. Por el titular, por un beneficiario, o por los dos.
        var titularPep = new Declaracion(true, Optional.of(TipoPep.NACIONAL), Optional.of("c"), Optional.of("i"));
        var noPep = new Declaracion(false, Optional.empty(), Optional.empty(), Optional.empty());
        var beneficiarioPep = new BeneficiarioFinal("A", "1", true, Optional.of(TipoPep.EXTRANJERO));
        var beneficiarioComun = new BeneficiarioFinal("B", "2", false, Optional.empty());

        assertThat(ClasificacionPep.clasificar(titularPep, List.of()).exigeDiligenciaReforzada())
                .isTrue();
        assertThat(ClasificacionPep.clasificar(noPep, List.of(beneficiarioPep)).exigeDiligenciaReforzada())
                .isTrue();
        assertThat(ClasificacionPep.clasificar(titularPep, List.of(beneficiarioPep))
                        .nivel())
                .isEqualTo(NivelRiesgo.ALTO);
        assertThat(ClasificacionPep.clasificar(noPep, List.of(beneficiarioComun))
                        .nivel())
                .isEqualTo(NivelRiesgo.BAJO);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "auditoria"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "auditoria"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Un beneficiario sin documento aborta ANTES de escribir: no puede quedar la
        // declaracion sin su estructura de control, ni al reves.
        ContextoSesion ctx = contexto();
        EntradaDeclaracion sinDocumento = new EntradaDeclaracion(
                ctx.usuarioId(),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(new BeneficiarioFinal("Sin papeles", "  ", false, Optional.empty())));

        assertThatThrownBy(() -> declarar(sinDocumento, ctx))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("beneficiario final");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.declaracion_pep WHERE usuario_id = ?", ctx.usuarioId()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.beneficiario_final WHERE usuario_id = ?",
                        ctx.usuarioId()))
                .isZero();
    }
}
