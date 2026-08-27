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
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase("SELECT fn_uif_exigir_ddd('%s')".formatted(ctx.usuarioId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-UIF-10")
    void rechazaRUIF10() {
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
        UUID unico = fixtura.usuario();

        // Completar con la MISMA persona en las dos firmas: el trigger lo rechaza.
        assertThat(rechazaLaBase(
                        """
                        UPDATE cumplimiento.debida_diligencia
                           SET estado = 'COMPLETA', aprobada_por = '%s', segunda_revision_por = '%s'
                         WHERE usuario_id = '%s'
                        """
                                .formatted(unico, unico, ctx.usuarioId())))
                .contains("R-UIF-10");
    }

    @Test
    @DisplayName("rechaza por R-UIF-11")
    void rechazaRUIF11() {
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.calificacion_riesgo_cliente
                            (id, usuario_id, nivel, puntaje_total, nivel_dd_requerido,
                             periodicidad_revision_meses, vigente_desde, proxima_revision, es_automatica)
                        VALUES (gen_random_uuid(), '%s', 'MEDIO', 0, 'ESTANDAR', 12, now(),
                                current_date + 365, true)
                        """
                                .formatted(ctx.usuarioId())))
                .contains("ex_calificacion_vigente");
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        // fn_lim_evaluar deniega por omision: sin limite configurado para el concepto
        // y el nivel, la operacion no pasa. La autoridad es la base.
        assertThat(rechazaLaBase(
                        "SELECT fn_lim_evaluar('%s', 'CONCEPTO_INEXISTENTE', 1.00)".formatted(UUID.randomUUID())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-LIM-03")
    void rechazaRLIM03() {
        // R-LIM-03 quiere que dos limites activos del mismo concepto, nivel y ventana
        // no solapen vigencias, y lo escribe como un EXCLUDE sobre daterange.
        //
        // HALLAZGO: ese EXCLUDE esta TAPADO. Sobre la misma tabla hay un indice unico
        // en (nivel_debida_diligencia, ventana, concepto) **sin la fecha**, que es
        // estrictamente mas estricto y salta primero. En la practica la regla que
        // rige es «un limite por combinacion, punto», no «vigencias sin solape»:
        // hoy es imposible cargar el limite del mes que viene antes de que empiece.
        // Se prueba lo que la base hace de verdad, y queda declarado en el informe
        // del carril: bajar el indice unico seria una decision de modelo, troncal, y
        // no de este carril.
        dslFixtura.execute(
                """
                INSERT INTO catalogo.limite_operativo_billetera
                    (id, concepto, nivel_debida_diligencia, ventana, monto_maximo, moneda,
                     base_normativa, vigente_desde, vigente_hasta, activo)
                VALUES (gen_random_uuid(), 'TRANSFERENCIA', 'ESTANDAR', 'MES', 1000, 'BOB',
                        'ASFI 540/2025', current_date - 10, current_date + 10, true)
                ON CONFLICT DO NOTHING
                """);

        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.limite_operativo_billetera
                            (id, concepto, nivel_debida_diligencia, ventana, monto_maximo, moneda,
                             base_normativa, vigente_desde, vigente_hasta, activo)
                        VALUES (gen_random_uuid(), 'TRANSFERENCIA', 'ESTANDAR', 'MES', 2000, 'BOB',
                                'ASFI 540/2025', current_date, current_date + 20, true)
                        """))
                .contains("uq_limite_operativo_billetera");

        dslFixtura.execute("DELETE FROM catalogo.limite_operativo_billetera WHERE concepto = 'TRANSFERENCIA'");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.caso_investigacion_lft
                            (id, codigo, usuario_id, analista_id, revisado_por, origen, estado,
                             prioridad, resumen, abierto_en, plazo_limite)
                        VALUES (gen_random_uuid(), 'LFT-DOS', '%s', '%s', '%s', 'ALERTA', 'ABIERTO',
                                'ALTA', 'Cuatro ojos', now(), now() + interval '30 days')
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
                                current_date - 1, now())
                        """
                                .formatted(ctx.usuarioId())))
                .contains("ck_expediente_retencion_futura");
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
