package bo.aportaya.cumplimiento.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Lee {@code catalogo.limite_operativo_billetera}: los topes que desbloquea un nivel.
 *
 * <p>Solo lectura. El consumo acumulado —{@code nucleo_financiero.consumo_limite}—
 * vive en otro esquema y no se toca desde aca (invariante 11): evaluar un limite
 * contra su consumo es CU-40, y por eso CU-40 pertenece al carril de la billetera,
 * no a este.
 */
@Component
public class LimiteRepositorio {

    /** Los topes vigentes de un nivel, para poder decir que desbloquea subir. */
    public List<Tope> vigentesPara(DSLContext dsl, String nivelDiligencia, LocalDate hoy) {
        return dsl.select(
                        DSL.field("concepto", String.class),
                        DSL.field("ventana", String.class),
                        DSL.field("monto_maximo", BigDecimal.class),
                        DSL.field("cantidad_maxima", Integer.class),
                        DSL.field("moneda", String.class))
                .from(DSL.table(DSL.name("catalogo", "limite_operativo_billetera")))
                .where(DSL.field("nivel_debida_diligencia").eq(nivelDiligencia))
                .and(DSL.field("activo", Boolean.class).isTrue())
                .and(DSL.field("vigente_desde", LocalDate.class).le(hoy))
                .and(DSL.field("vigente_hasta")
                        .isNull()
                        .or(DSL.field("vigente_hasta", LocalDate.class).ge(hoy)))
                .orderBy(DSL.field("concepto"), DSL.field("ventana"))
                .fetch(fila -> new Tope(
                        fila.get("concepto", String.class),
                        fila.get("ventana", String.class),
                        fila.get("monto_maximo", BigDecimal.class),
                        fila.get("cantidad_maxima", Integer.class),
                        fila.get("moneda", String.class)));
    }

    public record Tope(
            String concepto, String ventana, BigDecimal montoMaximo, Integer cantidadMaxima, String moneda) {}
}
