package bo.aportaya.notificaciones;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/** Filas minimas para probar el despacho, con las columnas del modelo real. */
final class FixturaDeNotificaciones {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(40_000_000);

    private final DSLContext dsl;

    FixturaDeNotificaciones(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Avisa', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "NOT-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /** Un evento notificable del catalogo, con su categoria y si es obligatorio. */
    UUID evento(String tipo, String categoria, boolean obligatorio, String prioridad) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO notificaciones.evento_notificable
                    (id, tipo, descripcion, categoria, es_obligatorio, prioridad, es_transaccional,
                     permite_agrupacion, ventana_deduplicacion_min, canales_permitidos, cadena_respaldo, activo)
                VALUES (?, ?, 'Evento de prueba', ?, ?, ?, true, false, 60, 'IN_APP,CORREO', 'IN_APP', true)
                """,
                id,
                tipo,
                categoria,
                obligatorio,
                prioridad);
        return id;
    }

    /** Un canal verificado del usuario: sin el, no hay por donde hablarle. */
    UUID canalVerificado(UUID usuarioId, String tipo, String identificador) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO notificaciones.canal_vinculado
                    (id, usuario_id, tipo, identificador, verificado, verificado_en,
                     rebotes_consecutivos, estado)
                VALUES (?, ?, ?, ?, true, now(), 0, 'ACTIVO')
                """,
                id,
                usuarioId,
                tipo,
                identificador);
        return id;
    }

    /** Plantilla aprobada con su version vigente. Sin aprobar, CU-80 no la usa. */
    UUID plantilla(String codigo, UUID eventoId, String canal, String cuerpo) {
        UUID plantillaId = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO notificaciones.plantilla_mensaje
                    (id, codigo, evento_id, canal, descripcion, categoria_proveedor,
                     estado_aprobacion, activa)
                VALUES (?, ?, ?, ?, 'Plantilla de prueba', 'UTILITY', 'APROBADA', true)
                """,
                plantillaId,
                codigo,
                eventoId,
                canal);
        UUID versionId = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO notificaciones.version_plantilla
                    (id, plantilla_id, version, idioma, asunto, cuerpo, variables, vigente_desde)
                VALUES (?, ?, 1, 'es', 'Aviso de prueba', ?, '[]'::jsonb, now() - interval '1 day')
                """,
                versionId,
                plantillaId,
                cuerpo);
        return versionId;
    }

    UUID proveedor(String codigo, String canales, int prioridad, BigDecimal costo, int salud, boolean activo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO notificaciones.proveedor_mensajeria
                    (id, codigo, nombre, canales_soportados, url_base, referencia_credenciales,
                     costo_por_mensaje, limite_mensajes_por_segundo, prioridad, activo, salud_porcentaje)
                VALUES (?, ?, ?, ?, 'https://proveedor.test', 'secreto/ref', ?, 10, ?, ?, ?)
                """,
                id,
                codigo,
                codigo,
                canales,
                costo,
                (short) prioridad,
                activo,
                new BigDecimal(salud));
        return id;
    }

    /** Deja el escenario en cero entre pruebas: comparten contenedor. */
    void limpiar() {
        for (String tabla : new String[] {
            "cola_muerta",
            "cola_envio",
            "envio_notificacion",
            "bandeja_entrada",
            "respuesta_entrante",
            "notificacion",
            "version_plantilla",
            "plantilla_mensaje",
            "lista_supresion",
            "proveedor_mensajeria",
            "programacion_recordatorio",
            "canal_vinculado",
            "evento_notificable",
            // El outbox tambien: sin esto, una prueba cuenta los eventos de la anterior
            // y pasa —o falla— por la razon equivocada.
            "evento_dominio"
        }) {
            // `DSL.name` en vez de concatenar: aunque la lista sea fija y este a la
            // vista, el nombre entra como identificador citado y no como texto SQL.
            dsl.deleteFrom(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name("notificaciones", tabla)))
                    .execute();
        }
    }
}
