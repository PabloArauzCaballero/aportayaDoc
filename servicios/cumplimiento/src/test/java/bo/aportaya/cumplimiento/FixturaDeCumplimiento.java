package bo.aportaya.cumplimiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/** Filas minimas para probar cumplimiento, con las columnas del modelo real. */
final class FixturaDeCumplimiento {

    /** Un telefono E.164 distinto por usuario: uq_usuario_telefono_e164 no perdona. */
    private static final AtomicInteger SECUENCIA = new AtomicInteger(30_000_000);

    private final DSLContext dsl;

    FixturaDeCumplimiento(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Un usuario real en {@code identidad}: las claves foraneas cruzan esquemas. */
    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Cumple', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "CUM-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /**
     * Deja la licencia de funcionamiento en el estado y alcance pedidos.
     *
     * <p>Inserta si no existe: {@code sql/aplicar.sql} crea el esquema pero **no
     * siembra** —las semillas son otro paso—, asi que en el contenedor de pruebas la
     * tabla arranca vacia y cada prueba pone las filas de catalogo que necesita.
     */
    void licencia(String estado, String alcanceJson, LocalDate vigenteHasta) {
        dsl.execute(
                """
                INSERT INTO catalogo.licencia_regulatoria
                    (id, organismo, tipo, categoria_actividad, estado, fecha_solicitud,
                     alcance_autorizado, vigente_hasta, fecha_otorgamiento)
                VALUES (gen_random_uuid(), 'ASFI', 'LICENCIA_FUNCIONAMIENTO',
                        'PAGOS_Y_PLATAFORMAS_DE_PAGO', ?, current_date - 100, ?::jsonb, ?,
                        CASE WHEN ? = 'OTORGADA' THEN current_date ELSE NULL END)
                """,
                estado,
                alcanceJson,
                vigenteHasta,
                estado);
    }

    UUID licenciaId() {
        return (UUID)
                dsl.fetchOne("SELECT id FROM catalogo.licencia_regulatoria WHERE tipo = 'LICENCIA_FUNCIONAMIENTO'")
                        .get(0);
    }

    /** Un entorno de prueba activo. ck_sandbox_limites exige los dos topes. */
    UUID sandbox(String servicio, int limiteUsuarios, BigDecimal limiteMonto) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.entorno_prueba_regulado
                    (id, licencia_regulatoria_id, servicio_en_prueba, alcance, limite_usuarios,
                     limite_monto_operacion, fecha_inicio, fecha_fin, estado, informes_remitidos)
                VALUES (?, ?, ?, '{}'::jsonb, ?, ?, current_date - 1, current_date + 30, 'ACTIVO', 0)
                """,
                id,
                licenciaId(),
                servicio,
                limiteUsuarios,
                limiteMonto);
        return id;
    }

    /** Devuelve la tabla al vacio con que arranca el contenedor. */
    void restaurarLicencia() {
        dsl.execute("DELETE FROM cumplimiento.entorno_prueba_regulado");
        dsl.execute("DELETE FROM catalogo.licencia_regulatoria WHERE tipo = 'LICENCIA_FUNCIONAMIENTO'");
    }
}
