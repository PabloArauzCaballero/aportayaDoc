package bo.aportaya.entregas.infraestructura;

import bo.aportaya.entregas.dominio.CuentaEnmascarada;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code cuenta_bancaria_beneficiario}.
 *
 * <p>El numero completo **no se guarda ni se selecciona nunca**: lo que sale de aca es
 * el hash, el enmascarado y el estado. Si una consulta devolviera el numero en claro,
 * terminaria en un log o en una respuesta HTTP sin que nadie lo decidiera.
 */
@Component
public class CuentaDestinoRepositorio {

    public Optional<Cuenta> ver(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("entregas", "cuenta_bancaria_beneficiario")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aCuenta);
    }

    /** La misma cuenta del mismo usuario, si ya la registro (uq_cuenta_benef_hash). */
    public Optional<Cuenta> porHash(DSLContext dsl, UUID usuarioId, String hash) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("entregas", "cuenta_bancaria_beneficiario")))
                .where(DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("hash_numero_cuenta", String.class).eq(hash)))
                .fetchOptional(this::aCuenta);
    }

    public int cuantasTiene(DSLContext dsl, UUID usuarioId) {
        return dsl.fetchCount(
                DSL.table(DSL.name("entregas", "cuenta_bancaria_beneficiario")),
                DSL.field("usuario_id", UUID.class).eq(usuarioId));
    }

    public UUID registrar(
            DSLContext dsl,
            UUID usuarioId,
            String tipoCuenta,
            String entidad,
            String numeroCifrado,
            CuentaEnmascarada enmascarada,
            String titularNombre,
            String titularDocumento,
            String moneda,
            boolean esPrincipal) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("entregas", "cuenta_bancaria_beneficiario")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("tipo_cuenta", String.class), tipoCuenta)
                .set(DSL.field("entidad_financiera", String.class), entidad)
                .set(DSL.field("numero_cuenta_cifrado", String.class), numeroCifrado)
                .set(DSL.field("version_llave", Short.class), (short) enmascarada.versionLlave())
                .set(DSL.field("hash_numero_cuenta", String.class), enmascarada.hash())
                .set(DSL.field("numero_enmascarado", String.class), enmascarada.enmascarado())
                .set(DSL.field("titular_nombre", String.class), titularNombre)
                .set(DSL.field("titular_documento", String.class), titularDocumento)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("es_principal", Boolean.class), esPrincipal)
                .set(DSL.field("estado_verificacion", String.class), "PENDIENTE")
                .execute();
        return id;
    }

    /**
     * Marca la cuenta verificada y **guarda** cuando queda disponible.
     *
     * <p>El plazo se persiste (invariante 8): recalcularlo al consultar haria que
     * acortar la politica liberara de golpe todas las cuentas que estaban enfriando.
     */
    public boolean verificar(
            DSLContext dsl, UUID id, String metodo, OffsetDateTime ahora, OffsetDateTime bloqueadaHasta) {
        return dsl.update(DSL.table(DSL.name("entregas", "cuenta_bancaria_beneficiario")))
                        .set(DSL.field("estado_verificacion", String.class), "VERIFICADA")
                        .set(DSL.field("metodo_verificacion", String.class), metodo)
                        .set(DSL.field("verificada_en", OffsetDateTime.class), ahora)
                        .set(DSL.field("bloqueada_hasta", OffsetDateTime.class), bloqueadaHasta)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado_verificacion", String.class)
                                        .eq("PENDIENTE")))
                        .execute()
                == 1;
    }

    /** Baja la principal anterior. Una sola por usuario (uq_cuenta_benef_principal). */
    public void quitarPrincipal(DSLContext dsl, UUID usuarioId) {
        dsl.update(DSL.table(DSL.name("entregas", "cuenta_bancaria_beneficiario")))
                .set(DSL.field("es_principal", Boolean.class), false)
                .where(DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("es_principal", Boolean.class).isTrue()))
                .execute();
    }

    public boolean designarPrincipal(DSLContext dsl, UUID id) {
        return dsl.update(DSL.table(DSL.name("entregas", "cuenta_bancaria_beneficiario")))
                        .set(DSL.field("es_principal", Boolean.class), true)
                        .where(DSL.field("id", UUID.class).eq(id))
                        .execute()
                == 1;
    }

    private java.util.List<org.jooq.Field<?>> campos() {
        return java.util.List.of(
                DSL.field("id", UUID.class),
                DSL.field("usuario_id", UUID.class),
                DSL.field("hash_numero_cuenta", String.class),
                DSL.field("numero_enmascarado", String.class),
                DSL.field("titular_nombre", String.class),
                DSL.field("titular_documento", String.class),
                DSL.field("moneda", String.class),
                DSL.field("es_principal", Boolean.class),
                DSL.field("estado_verificacion", String.class),
                DSL.field("verificada_en", OffsetDateTime.class),
                DSL.field("bloqueada_hasta", OffsetDateTime.class));
    }

    private Cuenta aCuenta(org.jooq.Record f) {
        return new Cuenta(
                f.get("id", UUID.class),
                f.get("usuario_id", UUID.class),
                f.get("hash_numero_cuenta", String.class),
                f.get("numero_enmascarado", String.class),
                f.get("titular_nombre", String.class),
                f.get("titular_documento", String.class),
                f.get("moneda", String.class),
                f.get("es_principal", Boolean.class),
                f.get("estado_verificacion", String.class),
                f.get("verificada_en", OffsetDateTime.class),
                f.get("bloqueada_hasta", OffsetDateTime.class));
    }

    public record Cuenta(
            UUID id,
            UUID usuarioId,
            String hash,
            String enmascarado,
            String titularNombre,
            String titularDocumento,
            String moneda,
            boolean esPrincipal,
            String estadoVerificacion,
            OffsetDateTime verificadaEn,
            OffsetDateTime bloqueadaHasta) {

        public boolean estaVerificada() {
            return "VERIFICADA".equals(estadoVerificacion);
        }
    }
}
