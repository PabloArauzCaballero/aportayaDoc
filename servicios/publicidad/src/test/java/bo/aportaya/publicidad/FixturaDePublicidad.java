package bo.aportaya.publicidad;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** Las filas que las pruebas de publicidad necesitan. */
class FixturaDePublicidad {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(80_800_000);

    private final DSLContext dsl;

    FixturaDePublicidad(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Publicidad', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'COMPLETO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "PUB-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /**
     * Un organizador en el estado que se pida.
     *
     * <p>Vive en el esquema de otro servicio. La fixtura lo escribe porque
     * {@code anunciante.organizador_id} tiene clave foranea contra el, y sin esa fila el
     * alta de CU-110 no se puede probar. Queda declarado como hueco del carril.
     */
    UUID organizador(String estado) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO organizador.organizador
                    (id, usuario_id, estado, nivel, limite_grupos_simultaneos, limite_monto_administrado,
                     grupos_activos, grupos_historicos, monto_administrado_actual, calificacion_promedio,
                     indice_morosidad_cartera, fecha_postulacion, version)
                VALUES (?, ?, ?, 'ESTANDAR', 5, 100000, 0, 0, 0, 0, 0, now(), 1)
                """,
                id,
                usuario(),
                estado);
        return id;
    }

    /** El usuario dueno de un organizador: el que R-PUB-05 no deja moderar. */
    UUID usuarioDelOrganizador(UUID organizadorId) {
        return dsl.fetchOne("SELECT usuario_id FROM organizador.organizador WHERE id = ?", organizadorId)
                .get(0, UUID.class);
    }

    UUID socio(String estado) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO publicidad.socio_comercial
                    (id, razon_social, numero_documento, email_contacto, estado, creado_en)
                VALUES (?, ?, ?, 'contacto@ejemplo.bo', ?, now())
                """,
                id,
                "Socio " + SECUENCIA.incrementAndGet(),
                "NIT-PUB-" + SECUENCIA.incrementAndGet(),
                estado);
        return id;
    }

    UUID espacio(String codigo, String tipo, int capacidad, boolean activo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO publicidad.espacio_publicitario
                    (id, codigo, nombre, tipo, capacidad_maxima_simultanea, activo)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                codigo,
                "Espacio " + codigo,
                tipo,
                (short) capacidad,
                activo);
        return id;
    }

    UUID segmento(UUID creadoPor) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO publicidad.segmento_audiencia (id, nombre, criterios, reutilizable, creado_por, creado_en)
                VALUES (?, ?, '{"ciudad": "Santa Cruz"}'::jsonb, true, ?, now())
                """,
                id,
                "Segmento " + SECUENCIA.incrementAndGet(),
                creadoPor);
        return id;
    }

    /** Una pieza creativa ya aprobada, sin pasar por el caso de uso. */
    UUID piezaAprobada(UUID anuncianteId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO publicidad.pieza_creativa
                    (id, anunciante_id, titulo, url_recurso, tipo_recurso, estado_moderacion, creada_en)
                VALUES (?, ?, ?, 'https://cdn.aportaya.bo/pieza.png', 'IMAGEN', 'APROBADA', now())
                """,
                id,
                anuncianteId,
                "Pieza " + SECUENCIA.incrementAndGet());
        return id;
    }

    /**
     * Una cuenta por cobrar de erp, con su tercero.
     *
     * <p>Vive en el esquema de otro servicio, y publicidad no la escribe en produccion:
     * la crea CU-104 y aca llega como dato. La fixtura la arma porque
     * {@code factura_publicidad.cuenta_por_cobrar_id} tiene clave foranea contra ella.
     */
    UUID cuentaPorCobrar(String monto) {
        UUID terceroId = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO erp.tercero_comercial (id, tipo, razon_social, numero_documento, estado)
                VALUES (?, 'CLIENTE', 'Anunciante de prueba', ?, 'ACTIVO')
                """,
                terceroId,
                "NIT-CXC-" + SECUENCIA.incrementAndGet());
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO erp.cuenta_por_cobrar
                    (id, origen_tipo, origen_id, tercero_comercial_id, monto, moneda,
                     monto_cobrado, fecha_vencimiento, estado)
                VALUES (?, 'FACTURA_PUBLICIDAD', gen_random_uuid(), ?, ?::numeric, 'BOB', 0,
                        current_date + 30, 'PENDIENTE')
                """,
                id,
                terceroId,
                monto);
        return id;
    }

    /** Una factura electronica de modulo 11: el comprobante fiscal del periodo. */
    UUID facturaElectronica(UUID usuarioId, String monto) {
        UUID datosId = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO tarifas.datos_facturacion
                    (id, usuario_id, tipo_documento, numero_documento, razon_social, email_envio, actualizado_en)
                VALUES (?, ?, 'NIT', ?, 'Anunciante de prueba', 'anunciante@ejemplo.bo', now())
                """,
                datosId,
                usuarioId,
                "NIT-FE-" + SECUENCIA.incrementAndGet());
        UUID id = UUID.randomUUID();
        int numero = SECUENCIA.incrementAndGet();
        dsl.execute(
                """
                INSERT INTO tarifas.factura_electronica
                    (id, usuario_id, datos_facturacion_id, nit_emisor, sucursal, punto_venta, numero_factura,
                     cuf, cufd, fecha_emision, monto_total, moneda, estado_fiscal, hash_documento)
                VALUES (?, ?, ?, '1234567890', 0, 0, ?, ?, ?, now(), ?::numeric, 'BOB', 'VALIDADA', ?)
                """,
                id,
                usuarioId,
                datosId,
                (long) numero,
                "CUF-" + numero,
                "CUFD-" + numero,
                monto,
                "hash-" + numero);
        return id;
    }

    /** Deja el esquema como estaba. Sin SQL concatenado: lo prohibe el contrato. */
    void limpiar() {
        for (String[] tabla : new String[][] {
            {"publicidad", "conversion_anuncio"},
            {"publicidad", "clic_anuncio"},
            {"publicidad", "impresion_anuncio"},
            {"publicidad", "anuncio"},
            {"publicidad", "factura_publicidad"}
        }) {
            dsl.deleteFrom(DSL.table(DSL.name(tabla[0], tabla[1]))).execute();
        }
    }
}
