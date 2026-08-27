package bo.aportaya.auditoria.infraestructura;

import bo.aportaya.auditoria.dominio.RetencionLegal;
import java.time.LocalDate;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Lee {@code auditoria.politica_retencion}.
 *
 * <p>Cuanto hay que conservar cada cosa es **catalogo con vigencia**, no una constante
 * en el codigo: la ley cambia los plazos y un despliegue no puede ser el requisito
 * para cumplir con el plazo nuevo (invariante 10).
 */
@Component
public class PoliticaRetencionRepositorio {

    private static final org.jooq.Name TABLA = DSL.name("auditoria", "politica_retencion");

    public List<RetencionLegal.Politica> vigentes(DSLContext dsl, LocalDate hoy) {
        return dsl.select(
                        DSL.field("entidad", String.class),
                        DSL.field("meses_retencion_activa", Integer.class),
                        DSL.field("meses_retencion_historica", Integer.class),
                        DSL.field("accion_al_vencer", String.class),
                        DSL.field("base_legal", String.class))
                .from(DSL.table(TABLA))
                .where(DSL.field("vigente_desde", LocalDate.class).lessOrEqual(hoy))
                .orderBy(DSL.field("entidad").asc())
                .fetch()
                .map(f -> new RetencionLegal.Politica(
                        f.get("entidad", String.class),
                        f.get("meses_retencion_activa", Integer.class),
                        f.get("meses_retencion_historica", Integer.class),
                        f.get("accion_al_vencer", String.class),
                        f.get("base_legal", String.class)));
    }
}
