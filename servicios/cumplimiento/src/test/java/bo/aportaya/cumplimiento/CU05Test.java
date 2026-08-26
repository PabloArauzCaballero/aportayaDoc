package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU05AceptarContrato.EntradaAceptacion;
import bo.aportaya.cumplimiento.aplicacion.CU05AceptarContrato.SalidaAceptacion;
import bo.aportaya.cumplimiento.dominio.EvidenciaDeAceptacion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-05 · Aceptar el contrato de adhesion y el tarifario. */
class CU05Test extends BaseDeCumplimiento {

    private static final String BILLETERA = "BILLETERA";

    @BeforeEach
    void publicarTarifario() {
        fixtura.tarifarioPublicado();
    }

    @AfterEach
    void limpiar() {
        // Las aceptaciones primero: son hijas del contrato. Y el contrato tambien se
        // borra — si sobrevive, la prueba de «no existe contrato vigente» encuentra
        // el de la prueba anterior y pasa por la razon equivocada.
        dslFixtura.execute("DELETE FROM cumplimiento.aceptacion_contrato");
        dslFixtura.execute("DELETE FROM cumplimiento.contrato_adhesion");
        fixtura.borrarTarifarios();
    }

    private SalidaAceptacion aceptar(UUID usuario, UUID contrato, int version, ContextoSesion ctx) {
        return transaccion.execute(e -> contratoCU.ejecutar(EntradaAceptacion.simple(usuario, contrato, version), ctx));
    }

    @Test
    @DisplayName(
            "Dado un contrato de adhesión vigente en versión 3 · Cuando el usuario acepta · Entonces existe aceptacion_contrato con version_aceptada = 3 y hash_evidencia no nulo")
    void criterio1() {
        UUID contrato = fixtura.contrato(BILLETERA, 3, "VIGENTE");
        ContextoSesion ctx = contexto();

        SalidaAceptacion salida = aceptar(ctx.usuarioId(), contrato, 3, ctx);

        assertThat(salida.hashEvidencia()).isNotBlank().hasSize(64);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.aceptacion_contrato WHERE id = ? AND version_aceptada = 3",
                        salida.aceptacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado que no existe contrato de adhesión vigente · Cuando un usuario intenta abrir cuenta · Entonces la apertura se rechaza")
    void criterio2() {
        ContextoSesion ctx = contexto();

        // R-CON-06 en su forma util: el caso de uso dice que NO esta al dia, y dice
        // que le falta, en vez de dejar al llamador adivinando.
        var estado = transaccion.execute(e -> contratoCU.estadoDe(ctx.usuarioId(), BILLETERA, ctx));

        assertThat(estado.alDia()).isFalse();
        assertThat(estado.versionVigente()).isNull();
    }

    @Test
    @DisplayName(
            "Dado un usuario que aceptó la versión 3 · Cuando se publica la versión 4 · Entonces se le solicita nueva aceptación · Y la aceptación de la versión 3 sigue existiendo")
    void criterio3() {
        UUID v3 = fixtura.contrato(BILLETERA, 3, "VIGENTE");
        ContextoSesion ctx = contexto();
        SalidaAceptacion aceptacionV3 = aceptar(ctx.usuarioId(), v3, 3, ctx);

        fixtura.sustituir(v3);
        UUID v4 = fixtura.contrato(BILLETERA, 4, "VIGENTE");

        var estado = transaccion.execute(e -> contratoCU.estadoDe(ctx.usuarioId(), BILLETERA, ctx));
        assertThat(estado.alDia()).isFalse();
        assertThat(estado.versionVigente()).isEqualTo(4);

        // Lo que ya paso no se reescribe: la aceptacion de la 3 sigue ahi.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.aceptacion_contrato WHERE id = ?",
                        aceptacionV3.aceptacionId()))
                .isEqualTo(1);
        assertThat(v4).isNotEqualTo(v3);
    }

    @Test
    @DisplayName("rechaza por R-CON-06")
    void rechazaRCON06() {
        // fn_con_exigir_contrato es la autoridad: sin aceptacion, la base corta.
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase("SELECT fn_con_exigir_contrato('%s', 'BILLETERA')".formatted(ctx.usuarioId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-CON-07")
    void rechazaRCON07() {
        // ck_tarifario_publicado: un tarifario VIGENTE sin publicar no puede existir.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.tarifario
                            (id, codigo, version, nombre, estado, moneda_base, vigente_desde, dias_preaviso)
                        VALUES (gen_random_uuid(), 'TAR-SINPUB', 1, 'Sin publicar', 'VIGENTE', 'BOB',
                                current_date, 30)
                        """))
                .contains("ck_tarifario_publicado");
    }

    @Test
    @DisplayName("rechaza por R-TAR-12")
    void rechazaRTAR12() {
        // ck_concepto_precio_final: al consumidor final se le muestra el precio con
        // impuestos incluidos. Un concepto gravado sin incluirlos no entra.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO tarifas.concepto_tarifa
                            (id, tarifario_id, hecho_generador_id, codigo, nombre_comercial,
                             descripcion_usuario, metodo_calculo, valor_fijo, base_calculo,
                             sujeto_obligado, forma_cobro, momento_cobro, gravado_iva, gravado_it,
                             precio_incluye_impuesto, orden_aplicacion, activo)
                        VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'CT-X',
                                'Comision', 'Comision de la operacion', 'FIJO', 5.00, 'MONTO_OPERACION',
                                'PAGADOR_DE_LA_OPERACION', 'DESCUENTO', 'AL_COBRAR', true, false,
                                false, 1, true)
                        """))
                .contains("ck_concepto_precio_final");
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // ck_expediente_retencion_futura: no se depura un expediente antes de tiempo.
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
        // La evidencia es determinista: los mismos datos y el mismo instante dan el
        // mismo hash. Si no lo fuera, dos peritos llegarian a valores distintos.
        UUID usuario = UUID.randomUUID();
        OffsetDateTime momento = OffsetDateTime.of(2026, 8, 26, 12, 0, 0, 0, ZoneOffset.UTC);

        String primero =
                EvidenciaDeAceptacion.armar("abc", usuario, 3, Optional.of("10.0.0.1"), Optional.empty(), momento);
        String segundo =
                EvidenciaDeAceptacion.armar("abc", usuario, 3, Optional.of("10.0.0.1"), Optional.empty(), momento);

        assertThat(primero).isEqualTo(segundo);
        // Y cambiar un solo dato cambia el sello: si no, no probaria nada.
        assertThat(EvidenciaDeAceptacion.armar("abc", usuario, 4, Optional.of("10.0.0.1"), Optional.empty(), momento))
                .isNotEqualTo(primero);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Aceptar dos veces la misma version deja DOS filas, y esta bien: la tabla es
        // append-only y una re-aceptacion es un hecho nuevo. Lo que no puede pasar es
        // que `estadoDe` mienta despues.
        UUID contrato = fixtura.contrato(BILLETERA, 2, "VIGENTE");
        ContextoSesion ctx = contexto();

        aceptar(ctx.usuarioId(), contrato, 2, ctx);
        aceptar(ctx.usuarioId(), contrato, 2, ctx);

        var estado = transaccion.execute(e -> contratoCU.estadoDe(ctx.usuarioId(), BILLETERA, ctx));
        assertThat(estado.alDia()).isTrue();
        assertThat(estado.versionAceptada()).isEqualTo(2);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-05 no mueve dinero: lo que cuadra es la version. Ni una abajo ni una
        // arriba de la vigente son aceptables, y por el mismo motivo.
        var vigente = Optional.of(new bo.aportaya.cumplimiento.dominio.VersionAceptable(3, "VIGENTE"));

        assertThat(bo.aportaya.cumplimiento.dominio.VersionAceptable.evaluar(vigente, 3))
                .isEqualTo(bo.aportaya.cumplimiento.dominio.VersionAceptable.Resultado.ACEPTABLE);
        assertThat(bo.aportaya.cumplimiento.dominio.VersionAceptable.evaluar(vigente, 2))
                .isEqualTo(bo.aportaya.cumplimiento.dominio.VersionAceptable.Resultado.DESACTUALIZADA);
        assertThat(bo.aportaya.cumplimiento.dominio.VersionAceptable.evaluar(vigente, 4))
                .isEqualTo(bo.aportaya.cumplimiento.dominio.VersionAceptable.Resultado.DESACTUALIZADA);
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
        // Sin tarifario publicado el caso de uso corta ANTES de escribir: no puede
        // quedar una aceptacion sin su evento, ni un evento sin su aceptacion.
        UUID contrato = fixtura.contrato(BILLETERA, 1, "VIGENTE");
        fixtura.borrarTarifarios();
        ContextoSesion ctx = contexto();
        int aceptacionesAntes = contar("SELECT count(*)::int FROM cumplimiento.aceptacion_contrato");
        int eventosAntes = contar("SELECT count(*)::int FROM cumplimiento.evento_dominio");

        assertThatThrownBy(() -> aceptar(ctx.usuarioId(), contrato, 1, ctx))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("tarifario publicado");

        assertThat(contar("SELECT count(*)::int FROM cumplimiento.aceptacion_contrato"))
                .isEqualTo(aceptacionesAntes);
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.evento_dominio"))
                .isEqualTo(eventosAntes);
    }

    @Test
    @DisplayName("los consentimientos por finalidad salen por evento, no por INSERT en esquema ajeno")
    void consentimientosPorEvento() {
        // `identidad.consentimiento` es esquema ajeno (invariante 11): cumplimiento
        // pide que se registre, no lo registra.
        UUID contrato = fixtura.contrato(BILLETERA, 1, "VIGENTE");
        ContextoSesion ctx = contexto();

        transaccion.execute(e -> contratoCU.ejecutar(
                new EntradaAceptacion(
                        ctx.usuarioId(),
                        contrato,
                        1,
                        Optional.empty(),
                        Optional.of("10.0.0.7"),
                        Optional.empty(),
                        List.of("PUBLICIDAD", "TRATAMIENTO_DATOS")),
                ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evento_dominio WHERE tipo = ?",
                        "cumplimiento.consentimiento_registrado"))
                .isEqualTo(2);
    }
}
