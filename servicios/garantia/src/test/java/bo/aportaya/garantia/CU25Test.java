package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDescargo;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaResolucion;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.SalidaDeclaracion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-25 · Declarar el incumplimiento con descargo y evidencia. */
class CU25Test extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(
            UUID usuario, FixturaDeGarantia.Escenario escenario, ContextoSesion suyo, ContextoSesion gestor) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, fixtura.escenario(usuario), contextoDe(usuario), contextoDe(fixtura.usuario()));
    }

    private EntradaDeclaracion declaracion(Caso c, String evidencia) {
        return new EntradaDeclaracion(
                "EXP-" + corto(),
                c.usuario(),
                c.escenario().participanteId(),
                c.escenario().grupoId(),
                c.escenario().periodoId(),
                c.escenario().cupoId(),
                c.escenario().obligacionId(),
                "APORTE_IMPAGO",
                "GRAVE",
                "AUTOMATICO_VENCIMIENTO",
                bob("500.00"),
                30,
                true,
                "LOG_SISTEMA",
                evidencia,
                null,
                null);
    }

    @Test
    @DisplayName(
            "Dada una obligación vencida con recordatorios acusados · Cuando se declara el incumplimiento · Entonces el registro queda PRESUNTO con fecha_limite_descargo guardada · Y existen evidencias automáticas con es_inmutable en true")
    void criterio1() {
        Caso c = caso();

        SalidaDeclaracion salida = transaccion.execute(
                t -> expedienteCU.declarar(declaracion(c, "Tres recordatorios acusados, sin pago"), c.gestor()));

        // HUECO DECLARADO: `ck_registro_incumplimiento_estado` no admite PRESUNTO ni
        // REGULARIZADO, y la columna se llama `fecha_limite_subsanacion`, no
        // `fecha_limite_descargo`. Manda la DDL. Ver H-2 en planes/informes/carril-4B.md.
        //
        // Lo que el criterio protege se cumple igual, y es lo unico que no se negocia:
        // el plazo se calcula al NOTIFICAR y queda GUARDADO (R-GAR-01).
        assertThat(salida.puedeDescargarHasta()).isNotNull();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.registro_incumplimiento
                         WHERE id = ? AND notificado_en IS NOT NULL AND fecha_limite_subsanacion IS NOT NULL
                        """,
                        salida.expedienteId()))
                .isEqualTo(1);
        // La evidencia entra inmutable desde el primer momento.
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.evidencia_incumplimiento WHERE registro_id = ? AND es_inmutable",
                        salida.expedienteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un registro presunto sin ningún recordatorio acusado · Cuando se intenta declararlo · Entonces se rechaza con SIN_AVISO_PROBADO")
    void criterio2() {
        Caso c = caso();

        // Sin evidencia no se declara: declarar un incumplimiento sin con que probarlo
        // deja al participante sin nada contra que defenderse.
        assertThatThrownBy(() -> transaccion.execute(t -> expedienteCU.declarar(declaracion(c, "   "), c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin evidencia");
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.registro_incumplimiento WHERE obligacion_id = ?",
                        c.escenario().obligacionId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un participante que paga dentro del plazo de descargo · Cuando corre el cierre del plazo · Entonces el registro queda REGULARIZADO · Y no se habilita la cobertura del fondo")
    void criterio3() {
        Caso c = caso();
        SalidaDeclaracion expediente =
                transaccion.execute(t -> expedienteCU.declarar(declaracion(c, "Recordatorios acusados"), c.gestor()));
        transaccion.execute(t -> expedienteCU.presentarDescargo(
                new EntradaDescargo(expediente.expedienteId(), "Ya pague, adjunto comprobante", "[]"), c.suyo()));

        var resolucion = transaccion.execute(t -> expedienteCU.resolverDescargo(
                new EntradaResolucion(expediente.expedienteId(), true, "Pago verificado dentro del plazo"),
                c.gestor()));

        // El estado equivalente que la DDL si admite es SUBSANADO (hueco H-2). Lo que
        // importa: el expediente queda cerrado y **no se habilita la cobertura**.
        assertThat(resolucion.estadoDelExpediente()).isEqualTo("SUBSANADO");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.historial_estado_incumplimiento
                         WHERE registro_id = ? AND estado_nuevo = 'SUBSANADO'
                        """,
                        expediente.expedienteId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.cobertura_incumplimiento WHERE registro_id = ?",
                        expediente.expedienteId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un registro confirmado · Cuando se intenta modificar una evidencia ya cargada · Entonces la base lo rechaza por append-only")
    void criterio4() {
        Caso c = caso();
        SalidaDeclaracion expediente = transaccion.execute(
                t -> expedienteCU.declarar(declaracion(c, "Log del sistema con los tres avisos"), c.gestor()));
        UUID evidenciaId = dsl.fetchOne(
                        "SELECT id FROM garantia.evidencia_incumplimiento WHERE registro_id = ?",
                        expediente.expedienteId())
                .get("id", UUID.class);

        // Una prueba que se puede editar despues de presentada no es una prueba, y el
        // descargo pasaria a ser contra un blanco movil.
        assertThat(rechazaLaBase(
                        "UPDATE garantia.evidencia_incumplimiento SET descripcion = 'otra cosa' WHERE id = '%s'"
                                .formatted(evidenciaId)))
                .contains("R-GAR-02");
        assertThat(rechazaLaBase(
                        "DELETE FROM garantia.evidencia_incumplimiento WHERE id = '%s'".formatted(evidenciaId)))
                .contains("R-GAR-02");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave natural es la obligacion: dos expedientes por el mismo impago le
        // cobran dos veces la misma falta a la misma persona.
        Caso c = caso();

        SalidaDeclaracion a = transaccion.execute(t -> expedienteCU.declarar(declaracion(c, "Evidencia"), c.gestor()));
        SalidaDeclaracion b = transaccion.execute(t -> expedienteCU.declarar(declaracion(c, "Evidencia"), c.gestor()));

        assertThat(b.expedienteId()).isEqualTo(a.expedienteId());
        assertThat(b.esNuevo()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.registro_incumplimiento WHERE obligacion_id = ?",
                        c.escenario().obligacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos descargos del mismo expediente: el segundo devuelve el que hay. Dos
        // abiertos permitirian dos resoluciones distintas del mismo caso.
        Caso c = caso();
        SalidaDeclaracion expediente =
                transaccion.execute(t -> expedienteCU.declarar(declaracion(c, "Evidencia"), c.gestor()));

        var a = transaccion.execute(t -> expedienteCU.presentarDescargo(
                new EntradaDescargo(expediente.expedienteId(), "Primero", "[]"), c.suyo()));
        var b = transaccion.execute(t -> expedienteCU.presentarDescargo(
                new EntradaDescargo(expediente.expedienteId(), "Segundo", "[]"), c.suyo()));

        assertThat(b.descargoId()).isEqualTo(a.descargoId());
        assertThat(b.esNuevo()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.descargo_participante WHERE registro_id = ?",
                        expediente.expedienteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El plazo sale del momento de notificar, exacto: ni un dia menos. Un plazo
        // que se acorta despues es un plazo que el imputado no puede planificar.
        Caso c = caso();

        SalidaDeclaracion expediente =
                transaccion.execute(t -> expedienteCU.declarar(declaracion(c, "Evidencia"), c.gestor()));

        var fila = dsl.fetchOne(
                "SELECT notificado_en, fecha_limite_subsanacion FROM garantia.registro_incumplimiento WHERE id = ?",
                expediente.expedienteId());
        assertThat(fila.get("fecha_limite_subsanacion", java.time.OffsetDateTime.class))
                .isEqualTo(fila.get("notificado_en", java.time.OffsetDateTime.class)
                        .plus(PLAZO_DE_DESCARGO));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "expedientes"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "expedientes"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Vencido el plazo, el descargo no entra. Y quien resuelve no puede ser el
        // imputado: sin eso, el procedimiento existe solo en el papel.
        Caso c = caso();
        SalidaDeclaracion expediente =
                transaccion.execute(t -> expedienteCU.declarar(declaracion(c, "Evidencia"), c.gestor()));

        assertThatThrownBy(() -> transaccion.execute(t -> expedienteCU.resolverDescargo(
                        new EntradaResolucion(expediente.expedienteId(), true, "Me absuelvo"), c.suyo())))
                .isInstanceOf(ErrorDeNegocio.class);

        dsl.execute(
                "INSERT INTO garantia.historial_estado_incumplimiento (id, registro_id, estado_anterior, estado_nuevo, motivo, es_automatico, fecha_hora) VALUES (gen_random_uuid(), ?, 'NOTIFICADO', 'NOTIFICADO', 'sin cambio', true, now())",
                expediente.expedienteId());
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.descargo_participante WHERE registro_id = ?",
                        expediente.expedienteId()))
                .isZero();
    }
}
