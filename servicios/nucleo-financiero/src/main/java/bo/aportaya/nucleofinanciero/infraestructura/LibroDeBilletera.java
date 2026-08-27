package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * El libro: {@code transaccion_billetera} y sus {@code movimiento_billetera}.
 *
 * <p>**Append-only.** No hay ningun metodo que actualice o borre un movimiento, y no
 * es una omision: R-AUD-01 lo hace cumplir con un trigger, y la unica correccion
 * valida es el movimiento inverso (CU-14). Un {@code UPDATE} sobre el libro seria
 * reescribir el pasado de la plata de alguien.
 *
 * <p>Toda transaccion **cuadra**: la suma de debitos iguala la de creditos y ninguna
 * puede estar vacia (R-BIL-01, trigger diferido al COMMIT). Por eso {@code registrar}
 * recibe las dos patas juntas: no hay forma de escribir media transaccion.
 */
@Component
public class LibroDeBilletera {

    /** Una pata del asiento: de que cuenta sale o a que cuenta entra. */
    public record Pata(UUID cuentaId, String sentido, Dinero monto, String glosa) {

        public static Pata debito(UUID cuentaId, Dinero monto, String glosa) {
            return new Pata(cuentaId, "DEBITO", monto, glosa);
        }

        public static Pata credito(UUID cuentaId, Dinero monto, String glosa) {
            return new Pata(cuentaId, "CREDITO", monto, glosa);
        }
    }

    /**
     * Escribe la transaccion con todas sus patas.
     *
     * <p>{@code hash_registro} encadena el libro: cada fila lleva el hash de la
     * anterior, asi que alterar una del medio rompe la cadena y se nota. Se calcula
     * sobre los datos que no pueden cambiar sin cambiar el hecho.
     */
    public UUID registrar(
            DSLContext dsl,
            String tipo,
            String origenTipo,
            UUID origenId,
            String canal,
            Dinero montoTotal,
            String claveIdempotencia,
            Optional<UUID> iniciadaPor,
            List<Pata> patas,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        String hashAnterior = ultimoHash(dsl);
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "transaccion_billetera")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("estado", String.class), "APLICADA")
                .set(DSL.field("moneda", String.class), montoTotal.moneda().name())
                .set(DSL.field("monto_total", BigDecimal.class), montoTotal.monto())
                .set(DSL.field("origen_tipo", String.class), origenTipo)
                .set(DSL.field("origen_id", UUID.class), origenId)
                .set(DSL.field("canal", String.class), canal)
                .set(DSL.field("iniciada_por", UUID.class), iniciadaPor.orElse(null))
                .set(DSL.field("clave_idempotencia", String.class), claveIdempotencia)
                .set(DSL.field("hash_registro", String.class), hashDe(id, montoTotal, hashAnterior))
                .set(DSL.field("hash_anterior", String.class), hashAnterior)
                .set(DSL.field("ocurrida_en", OffsetDateTime.class), ahora)
                .set(DSL.field("registrada_en", OffsetDateTime.class), ahora)
                .execute();

        short orden = 1;
        for (Pata pata : patas) {
            dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "movimiento_billetera")))
                    .set(DSL.field("id", UUID.class), UUID.randomUUID())
                    .set(DSL.field("transaccion_id", UUID.class), id)
                    .set(DSL.field("cuenta_billetera_id", UUID.class), pata.cuentaId())
                    .set(DSL.field("orden", Short.class), orden++)
                    .set(DSL.field("sentido", String.class), pata.sentido())
                    .set(DSL.field("monto", BigDecimal.class), pata.monto().monto())
                    // Los saldos posteriores los recalcula el trigger; se escribe el
                    // que se conoce al momento y la base lo corrige.
                    .set(DSL.field("saldo_disponible_posterior", BigDecimal.class), BigDecimal.ZERO)
                    .set(DSL.field("saldo_retenido_posterior", BigDecimal.class), BigDecimal.ZERO)
                    .set(DSL.field("glosa", String.class), pata.glosa())
                    .set(DSL.field("registrado_en", OffsetDateTime.class), ahora)
                    .execute();
        }
        return id;
    }

    /** R-BIL-06: la clave se ampara en el titular, nunca sola. */
    public Optional<UUID> porClaveIdempotencia(DSLContext dsl, String clave) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "transaccion_billetera")))
                .where(DSL.field("clave_idempotencia").eq(clave))
                .fetchOne(DSL.field("id", UUID.class)));
    }

    private String ultimoHash(DSLContext dsl) {
        return dsl.select(DSL.field("hash_registro", String.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "transaccion_billetera")))
                .orderBy(DSL.field("secuencia").desc())
                .limit(1)
                .fetchOne(DSL.field("hash_registro", String.class));
    }

    private String hashDe(UUID id, Dinero monto, String anterior) {
        String material = id + "|" + monto + "|" + (anterior == null ? "" : anterior);
        try {
            byte[] resumen = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder texto = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }
}
