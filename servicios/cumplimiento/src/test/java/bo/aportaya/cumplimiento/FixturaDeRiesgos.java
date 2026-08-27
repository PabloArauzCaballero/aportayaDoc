package bo.aportaya.cumplimiento;

import bo.aportaya.cumplimiento.infraestructura.RiesgoOperativoRepositorio;
import java.time.LocalDate;
import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Lo que las pruebas de riesgo operativo necesitan mirar y torcer.
 *
 * <p>Aparte de {@link FixturaDeCumplimiento} porque son otras tablas y otro caso de uso.
 * Escribe con un {@code DSLContext} que <b>no</b> pasa por la transaccion del caso de
 * uso: si escribiera dentro de la misma, la prueba comprobaria que el caso de uso ve lo
 * que ella misma acaba de poner, y no lo que hay en la base.
 */
class FixturaDeRiesgos {

    private final DSLContext dsl;

    FixturaDeRiesgos(DSLContext dsl) {
        this.dsl = dsl;
    }

    String categoriaDelEvento(UUID eventoId) {
        return dsl.fetchOne("SELECT categoria_evento FROM cumplimiento.evento_riesgo_operativo WHERE id = ?", eventoId)
                .get(0, String.class);
    }

    String factorDelEvento(UUID eventoId) {
        return dsl.fetchOne("SELECT factor_riesgo FROM cumplimiento.evento_riesgo_operativo WHERE id = ?", eventoId)
                .get(0, String.class);
    }

    /**
     * Adelanta el compromiso de un plan para probar el vencimiento sin esperar.
     *
     * <p>Se hace desde la fixtura y no pasando una fecha vieja al caso de uso a
     * proposito: el compromiso original tiene que ser futuro —es un plan, no un
     * historial— y lo que la prueba necesita es que <b>pase el tiempo</b>, no que el
     * plan naciera vencido.
     */
    void vencerPlan(UUID planId, LocalDate compromiso) {
        dsl.execute("UPDATE cumplimiento.plan_accion_riesgo SET fecha_compromiso = ? WHERE id = ?", compromiso, planId);
    }

    String estadoDelPlan(UUID planId) {
        return dsl.fetchOne("SELECT estado FROM cumplimiento.plan_accion_riesgo WHERE id = ?", planId)
                .get(0, String.class);
    }

    boolean hallazgoAbiertoDelPlan(UUID planId) {
        Number cuantos = (Number) dsl.fetchOne(
                        "SELECT count(*) FROM cumplimiento.hallazgo_auditoria WHERE codigo = ? AND estado = 'ABIERTO'",
                        RiesgoOperativoRepositorio.codigoDerivadoDe(planId))
                .get(0);
        return cuantos.intValue() > 0;
    }

    int hallazgosDelPlan(UUID planId) {
        Number cuantos = (Number) dsl.fetchOne(
                        "SELECT count(*) FROM cumplimiento.hallazgo_auditoria WHERE codigo = ?",
                        RiesgoOperativoRepositorio.codigoDerivadoDe(planId))
                .get(0);
        return cuantos.intValue();
    }
}
