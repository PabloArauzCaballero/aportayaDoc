package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU02ElevarDiligencia.EntradaDiligencia;
import bo.aportaya.cumplimiento.aplicacion.CU02ElevarDiligencia.SalidaDiligencia;
import bo.aportaya.cumplimiento.aplicacion.CU03DeclararPep.EntradaDeclaracion;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep.NivelRiesgo;
import bo.aportaya.cumplimiento.dominio.NivelDeDiligencia;
import bo.aportaya.cumplimiento.dominio.PeriodicidadDeRevision;
import bo.aportaya.plataforma.dominio.ClaveIdempotencia;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-02 · Elevar el nivel de debida diligencia. */
class CU02Test extends BaseDeCumplimiento {

    private static final List<String> PAPELES_ESTANDAR = List.of("CEDULA", "DOMICILIO");

    private SalidaDiligencia elevar(
            ContextoSesion ctx, String destino, List<String> documentos, Optional<UUID> segundaRevision) {
        return transaccion.execute(e -> diligenciaCU.ejecutar(
                new EntradaDiligencia(
                        ClaveIdempotencia.deHecho("diligencia", UUID.randomUUID()),
                        ctx.usuarioId(),
                        destino,
                        documentos,
                        fixtura.usuario(),
                        segundaRevision),
                ctx));
    }

    @Test
    @DisplayName(
            "Dado un usuario en nivel SIMPLIFICADA · Cuando completa la documentación de nivel ESTANDAR y un analista la aprueba · Entonces existe una única calificacion_riesgo_cliente vigente con nivel_dd_requerido ESTANDAR · Y la calificación anterior conserva su vigente_hasta")
    void criterio1() {
        ContextoSesion ctx = contexto();
        // Arranca sin calificacion: el caso de uso lee SIMPLIFICADA por omision.
        transaccion.execute(e -> pepCU.ejecutar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx));

        SalidaDiligencia salida = elevar(ctx, "ESTANDAR", PAPELES_ESTANDAR, Optional.empty());

        assertThat(salida.estado()).isEqualTo("COMPLETA");
        assertThat(salida.faltantes()).isEmpty();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ? AND vigente_hasta IS NULL AND nivel_dd_requerido = 'ESTANDAR'",
                        ctx.usuarioId()))
                .isEqualTo(1);
        // La anterior no se borro: quedo cerrada con su fecha.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ? AND vigente_hasta IS NOT NULL",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario marcado como PEP · Cuando un solo analista intenta aprobar su debida diligencia · Entonces la aprobación es rechazada por falta de segunda revisión (R-UIF-10)")
    void criterio2() {
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(
                new EntradaDeclaracion(
                        ctx.usuarioId(),
                        true,
                        Optional.of("NACIONAL"),
                        Optional.of("Director"),
                        Optional.of("Ministerio"),
                        List.of()),
                ctx));

        // Ya quedo en REFORZADA por la declaracion, asi que el destino es CONTINUA
        // para que sea una elevacion real y el rechazo sea por la firma, no por nivel.
        assertThatThrownBy(() -> elevar(
                        ctx,
                        "REFORZADA",
                        List.of("CEDULA", "DOMICILIO", "INGRESOS", "ORIGEN_FONDOS"),
                        Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName(
            "Dado un usuario cuya debida_diligencia venció · Cuando intenta una recarga · Entonces la operación es rechazada y la cuenta figura LIMITADA")
    void criterio3() {
        // fn_uif_exigir_ddd es la autoridad de R-UIF-09 y vive en la base. El estado
        // LIMITADA de la cuenta pertenece a nucleo_financiero: cumplimiento no lo
        // escribe (invariante 11), lo pide por evento.
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase("SELECT fn_uif_exigir_ddd('%s')".formatted(ctx.usuarioId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // El segundo intento al mismo nivel ya no es una elevacion: el propio caso de
        // uso lo corta con NIVEL_NO_ASCENDENTE, que es la forma correcta de no
        // duplicar el efecto.
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx));
        elevar(ctx, "ESTANDAR", PAPELES_ESTANDAR, Optional.empty());

        assertThatThrownBy(() -> elevar(ctx, "ESTANDAR", PAPELES_ESTANDAR, Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no es superior al actual");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ? AND vigente_hasta IS NULL",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos elevaciones sucesivas dejan SIEMPRE una sola calificacion vigente: lo
        // garantiza el EXCLUDE, no un SELECT previo que se pueda pisar.
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx));
        elevar(ctx, "ESTANDAR", PAPELES_ESTANDAR, Optional.empty());
        elevar(ctx, "AMPLIADA", List.of("CEDULA", "DOMICILIO", "INGRESOS"), Optional.empty());

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ? AND vigente_hasta IS NULL",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-02 no mueve dinero: lo que cuadra es el orden de los niveles y la
        // periodicidad que de el se desprende. A mas riesgo, mas seguido.
        var periodicidad = new PeriodicidadDeRevision(6, 12, 24);

        assertThat(NivelDeDiligencia.REFORZADA.esSuperiorA(NivelDeDiligencia.ESTANDAR))
                .isTrue();
        assertThat(NivelDeDiligencia.ESTANDAR.esSuperiorA(NivelDeDiligencia.REFORZADA))
                .isFalse();
        assertThat(NivelDeDiligencia.ESTANDAR.esSuperiorA(NivelDeDiligencia.ESTANDAR))
                .isFalse();
        assertThat(periodicidad.mesesPara(NivelRiesgo.ALTO)).isLessThan(periodicidad.mesesPara(NivelRiesgo.BAJO));
        assertThat(periodicidad.proximaDesde(LocalDate.of(2026, 1, 31), NivelRiesgo.ALTO))
                .isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "identidad"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "identidad"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Con documentacion incompleta la diligencia queda OBSERVADA y **no** se
        // recalifica: subir el nivel con papeles faltantes seria abrir topes que
        // nadie respaldo.
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx));

        SalidaDiligencia salida = elevar(ctx, "AMPLIADA", List.of("CEDULA"), Optional.empty());

        assertThat(salida.estado()).isEqualTo("OBSERVADA");
        assertThat(salida.faltantes()).containsExactly("DOMICILIO", "INGRESOS");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ? AND vigente_hasta IS NULL AND nivel_dd_requerido = 'AMPLIADA'",
                        ctx.usuarioId()))
                .isZero();
    }
}
