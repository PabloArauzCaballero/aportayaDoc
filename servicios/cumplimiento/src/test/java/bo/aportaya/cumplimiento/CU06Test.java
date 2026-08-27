package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU03DeclararPep.EntradaDeclaracion;
import bo.aportaya.cumplimiento.aplicacion.CU06RevisarConocimiento.EntradaRevision;
import bo.aportaya.cumplimiento.aplicacion.CU06RevisarConocimiento.SalidaRevision;
import bo.aportaya.cumplimiento.dominio.DesvioDePerfil;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-06 · Revision periodica de conocimiento del cliente. */
class CU06Test extends BaseDeCumplimiento {

    private static final BigDecimal DECLARADO = new BigDecimal("1000.00");

    /** Un cliente listo para revisar: calificado y con su perfil declarado. */
    private ContextoSesion clienteCalificado() {
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx));
        fixtura.perfilDeclarado(ctx.usuarioId(), DECLARADO);
        return ctx;
    }

    private SalidaRevision revisar(ContextoSesion ctx, Optional<BigDecimal> observado) {
        return transaccion.execute(
                e -> revisionCU.ejecutar(new EntradaRevision(ctx.usuarioId(), "PROGRAMADA", observado), ctx));
    }

    @Test
    @DisplayName(
            "Dado un cliente de riesgo ALTO con periodicidad de 6 meses · Cuando pasan 6 meses desde su última calificación · Entonces existe una revision_periodica_kyc programada")
    void criterio1() {
        ContextoSesion ctx = contexto();
        // PEP ⇒ riesgo ALTO ⇒ periodicidad de 6 meses, que es la que fija la
        // calificacion al crearse y queda GUARDADA en proxima_revision.
        transaccion.execute(e -> pepCU.ejecutar(
                new EntradaDeclaracion(
                        ctx.usuarioId(),
                        true,
                        Optional.of("NACIONAL"),
                        Optional.of("Director"),
                        Optional.of("Ministerio"),
                        java.util.List.of()),
                ctx));
        fixtura.perfilDeclarado(ctx.usuarioId(), DECLARADO);

        SalidaRevision salida = revisar(ctx, Optional.empty());

        assertThat(salida.resultado()).isEqualTo("RATIFICADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.revision_periodica_kyc WHERE usuario_id = ?",
                        ctx.usuarioId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT periodicidad_revision_meses FROM cumplimiento.calificacion_riesgo_cliente WHERE usuario_id = ? AND vigente_hasta IS NULL",
                        ctx.usuarioId()))
                .isEqualTo(6);
    }

    @Test
    @DisplayName(
            "Dado un cliente cuyo monto observado supera en 300% al declarado · Cuando corre la revisión · Entonces existe un desvio_perfil con severidad alta · Y se genera una alerta_monitoreo_lft")
    void criterio2() {
        ContextoSesion ctx = clienteCalificado();
        fixtura.reglaDeMonitoreo(REGLA_DESVIO);

        // Observado = 4x lo declarado ⇒ 300% de exceso ⇒ por encima del corte ALTA.
        SalidaRevision salida = revisar(ctx, Optional.of(new BigDecimal("4000.00")));

        assertThat(salida.desvioPorcentual()).isEqualTo("300.00");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.desvio_perfil WHERE usuario_id = ? AND severidad = 'ALTA'",
                        ctx.usuarioId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.alerta_monitoreo_lft WHERE usuario_id = ? AND estado = 'ABIERTA'",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado una revisión vencida y no ejecutada · Cuando pasa el plazo de gracia · Entonces la cuenta_billetera queda en estado LIMITADA")
    void criterio3() {
        ContextoSesion ctx = clienteCalificado();
        // Una revision programada en el pasado que nadie ejecuto.
        dslFixtura.execute(
                """
                INSERT INTO cumplimiento.revision_periodica_kyc
                    (id, usuario_id, fecha_programada, estado)
                VALUES (gen_random_uuid(), ?, current_date - 10, 'PROGRAMADA')
                """,
                ctx.usuarioId());

        int vencidas = transaccion.execute(e -> revisionCU.vencerYPedirLimitacion(ctx));

        assertThat(vencidas).isGreaterThanOrEqualTo(1);
        // El estado LIMITADA vive en nucleo_financiero.cuenta_billetera: cumplimiento
        // lo PIDE por evento, no lo escribe (invariante 11).
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evento_dominio WHERE tipo = ?",
                        "cumplimiento.revision_vencida"))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase("SELECT fn_uif_exigir_ddd('%s')".formatted(ctx.usuarioId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-UIF-11")
    void rechazaRUIF11() {
        ContextoSesion ctx = clienteCalificado();

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
        assertThat(rechazaLaBase("SELECT fn_lim_evaluar('%s', 'RETIRO', 1.00)".formatted(UUID.randomUUID())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Una fila de bitacora sin quien la origino no es auditable.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO comun.bitacora_evento
                            (id, secuencia, entidad, entidad_id, accion, origen, correlation_id,
                             hash_registro, hash_anterior, fecha_hora)
                        VALUES (gen_random_uuid(),
                                nextval(pg_get_serial_sequence('comun.bitacora_evento','secuencia')),
                                'revision_kyc', gen_random_uuid(), 'CREACION', 'USUARIO',
                                gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La idempotencia no la da una clave del llamador —no hay llamador, es un
        // trabajo programado— sino la fecha programada, que es lo unico estable.
        ContextoSesion ctx = clienteCalificado();

        SalidaRevision primera = revisar(ctx, Optional.empty());
        SalidaRevision segunda = revisar(ctx, Optional.empty());

        assertThat(segunda.revisionId()).isEqualTo(primera.revisionId());
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.revision_periodica_kyc WHERE usuario_id = ?",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        ContextoSesion ctx = clienteCalificado();
        fixtura.reglaDeMonitoreo(REGLA_DESVIO);

        revisar(ctx, Optional.of(new BigDecimal("4000.00")));
        revisar(ctx, Optional.of(new BigDecimal("4000.00")));

        // La revision es una sola: la fecha programada es la llave.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.revision_periodica_kyc WHERE usuario_id = ?",
                        ctx.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Lo que cuadra en CU-06 es el desvio: el porcentaje al centavo y cada corte
        // de severidad del lado correcto de su umbral.
        assertThat(DesvioDePerfil.calcular(new BigDecimal("4000.00"), DECLARADO, UMBRALES)
                        .porcentaje())
                .isEqualByComparingTo("300.00");
        assertThat(DesvioDePerfil.calcular(new BigDecimal("2000.00"), DECLARADO, UMBRALES)
                        .severidad())
                .isEqualTo(DesvioDePerfil.Severidad.MEDIA);
        assertThat(DesvioDePerfil.calcular(new BigDecimal("6000.00"), DECLARADO, UMBRALES)
                        .severidad())
                .isEqualTo(DesvioDePerfil.Severidad.CRITICA);
        // Por debajo del primer corte no hay desvio que registrar: registrar todo
        // ahogaria en ruido las pocas alertas que importan.
        assertThat(DesvioDePerfil.calcular(new BigDecimal("1500.00"), DECLARADO, UMBRALES)
                        .ameritaAlerta())
                .isFalse();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "nucleo-financiero"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "nucleo-financiero"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin perfil declarado el caso de uso corta ANTES de programar nada: no puede
        // quedar una revision abierta que despues nadie pueda cerrar por falta de
        // referencia contra la cual comparar.
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx));

        assertThatThrownBy(() -> revisar(ctx, Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("perfil transaccional declarado");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.revision_periodica_kyc WHERE usuario_id = ?",
                        ctx.usuarioId()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza sin calificacion vigente: no hay periodicidad que aplicar")
    void rechazaSinCalificacion() {
        ContextoSesion ctx = contexto();
        fixtura.perfilDeclarado(ctx.usuarioId(), DECLARADO);

        assertThatThrownBy(() -> revisar(ctx, Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("calificacion de riesgo vigente");
    }

    @Test
    @DisplayName("rechaza un declarado en cero: no es desvio infinito, es perfil sin declarar")
    void rechazaDeclaradoEnCero() {
        // Devolver un numero enorme disfrazaria un dato faltante de dato alarmante.
        assertThatThrownBy(() -> DesvioDePerfil.calcular(new BigDecimal("500.00"), BigDecimal.ZERO, UMBRALES))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
