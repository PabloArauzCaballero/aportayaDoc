package bo.aportaya.publicidad.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code socio_comercial}, {@code anunciante} y {@code cuenta_publicitaria}. */
@Component
public class AnuncianteRepositorio {

    private static final String ESQUEMA = "publicidad";

    public UUID altaDeSocio(
            DSLContext dsl,
            String razonSocial,
            String numeroDocumento,
            String rubro,
            String emailContacto,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "socio_comercial")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("razon_social", String.class), razonSocial)
                .set(DSL.field("numero_documento", String.class), numeroDocumento)
                .set(DSL.field("rubro", String.class), rubro)
                .set(DSL.field("email_contacto", String.class), emailContacto)
                .set(DSL.field("estado", String.class), "POSTULADO")
                .set(DSL.field("creado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** Operaciones verifica al socio y lo deja ACTIVO, con su responsable. */
    public boolean verificarSocio(DSLContext dsl, UUID socioId, UUID verificadoPor) {
        return dsl.update(DSL.table(DSL.name(ESQUEMA, "socio_comercial")))
                        .set(DSL.field("estado", String.class), "ACTIVO")
                        .set(DSL.field("verificado_por", UUID.class), verificadoPor)
                        .where(DSL.field("id", UUID.class).eq(socioId))
                        .and(DSL.field("estado", String.class).eq("POSTULADO"))
                        .execute()
                == 1;
    }

    public Optional<Socio> socio(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("razon_social", String.class),
                        DSL.field("estado", String.class))
                .from(DSL.table(DSL.name(ESQUEMA, "socio_comercial")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Socio(
                        f.get("id", UUID.class), f.get("razon_social", String.class), f.get("estado", String.class)));
    }

    public Optional<String> socioPorDocumento(DSLContext dsl, String numeroDocumento) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name(ESQUEMA, "socio_comercial")))
                .where(DSL.field("numero_documento", String.class).eq(numeroDocumento))
                .fetchOptional(f -> f.get("id", UUID.class).toString());
    }

    public UUID altaDeAnunciante(
            DSLContext dsl,
            String tipo,
            UUID organizadorId,
            UUID socioComercialId,
            String razonSocialFacturacion,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "anunciante")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("organizador_id", UUID.class), organizadorId)
                .set(DSL.field("socio_comercial_id", UUID.class), socioComercialId)
                .set(DSL.field("razon_social_facturacion", String.class), razonSocialFacturacion)
                .set(DSL.field("estado", String.class), "ACTIVO")
                .set(DSL.field("creado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public UUID abrirCuenta(
            DSLContext dsl, UUID anuncianteId, BigDecimal limiteGastoMensual, String moneda, OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "cuenta_publicitaria")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("anunciante_id", UUID.class), anuncianteId)
                .set(DSL.field("limite_gasto_mensual", BigDecimal.class), limiteGastoMensual)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("saldo_consumido_mes", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("estado", String.class), "ACTIVA")
                .set(DSL.field("creada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /**
     * La cuenta, con bloqueo.
     *
     * <p>{@code FOR UPDATE} porque el limite del mes se decide leyendo el consumido: sin
     * bloquear, dos campanas aprobadas a la vez leen el mismo saldo y las dos pasan.
     */
    public Optional<Cuenta> cuentaBloqueada(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("anunciante_id", UUID.class),
                        DSL.field("limite_gasto_mensual", BigDecimal.class),
                        DSL.field("saldo_consumido_mes", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class))
                .from(DSL.table(DSL.name(ESQUEMA, "cuenta_publicitaria")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(f -> new Cuenta(
                        f.get("id", UUID.class),
                        f.get("anunciante_id", UUID.class),
                        f.get("limite_gasto_mensual", BigDecimal.class),
                        f.get("saldo_consumido_mes", BigDecimal.class),
                        f.get("moneda", String.class),
                        f.get("estado", String.class)));
    }

    /** Suma al consumido del mes. Lo acota {@code ck_cuenta_publicitaria_consumo}. */
    public void sumarConsumo(DSLContext dsl, UUID cuentaId, BigDecimal monto) {
        dsl.update(DSL.table(DSL.name(ESQUEMA, "cuenta_publicitaria")))
                .set(
                        DSL.field("saldo_consumido_mes", BigDecimal.class),
                        DSL.field("saldo_consumido_mes", BigDecimal.class).plus(monto))
                .where(DSL.field("id", UUID.class).eq(cuentaId))
                .execute();
    }

    public record Socio(UUID id, String razonSocial, String estado) {}

    public record Cuenta(
            UUID id,
            UUID anuncianteId,
            BigDecimal limiteGastoMensual,
            BigDecimal saldoConsumidoMes,
            String moneda,
            String estado) {}
}
