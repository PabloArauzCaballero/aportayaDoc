package bo.aportaya.aportes.infraestructura;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code proveedor_pago}.
 *
 * <p>{@code referencia_credenciales} guarda una REFERENCIA al almacen de secretos,
 * nunca el secreto. Es una de las dieciocho prohibiciones, y la razon es simple: una
 * clave en una fila aparece despues en un respaldo, en un volcado de desarrollo y en
 * la pantalla de cualquiera con lectura.
 */
@Component
public class ProveedorPagoRepositorio {

    public boolean existeCodigo(DSLContext dsl, String codigo) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("aportes", "proveedor_pago")),
                        DSL.field("codigo").eq(codigo))
                > 0;
    }

    public UUID crear(
            DSLContext dsl,
            String codigo,
            String nombre,
            String tipo,
            String urlBase,
            String referenciaCredenciales,
            BigDecimal comisionFija,
            BigDecimal comisionPorcentual,
            boolean soportaWebhook,
            boolean soportaConsultaEstado,
            int prioridad) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("aportes", "proveedor_pago")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("nombre", String.class), nombre)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("url_base", String.class), urlBase)
                .set(DSL.field("referencia_credenciales", String.class), referenciaCredenciales)
                .set(DSL.field("comision_fija", BigDecimal.class), comisionFija)
                .set(DSL.field("comision_porcentual", BigDecimal.class), comisionPorcentual)
                .set(DSL.field("soporta_webhook", Boolean.class), soportaWebhook)
                .set(DSL.field("soporta_consulta_estado", Boolean.class), soportaConsultaEstado)
                .set(DSL.field("activo", Boolean.class), true)
                .set(DSL.field("prioridad", Short.class), (short) prioridad)
                .execute();
        return id;
    }

    /**
     * Los activos, en orden de prioridad.
     *
     * <p>La salud <b>no sale de aca</b>: {@code aportes.proveedor_pago} no tiene esa
     * columna y este repositorio no la va a inventar. La aporta quien la mide, y el
     * caso de uso la recibe como entrada.
     */
    public List<Candidato> activos(DSLContext dsl) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("prioridad", Short.class),
                        DSL.field("soporta_consulta_estado", Boolean.class))
                .from(DSL.table(DSL.name("aportes", "proveedor_pago")))
                .where(DSL.field("activo", Boolean.class).isTrue())
                .orderBy(DSL.field("prioridad").asc())
                .fetch(f -> new Candidato(
                        f.get("id", UUID.class),
                        f.get("codigo", String.class),
                        f.get("prioridad", Short.class),
                        f.get("soporta_consulta_estado", Boolean.class)));
    }

    public record Candidato(UUID id, String codigo, int prioridad, boolean soportaConsultaEstado) {}
}
