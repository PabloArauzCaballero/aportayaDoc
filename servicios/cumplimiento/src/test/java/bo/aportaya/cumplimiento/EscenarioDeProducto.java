package bo.aportaya.cumplimiento;

import bo.aportaya.cumplimiento.aplicacion.CU47EvaluarRiesgoDeProducto.EntradaEvaluacion;
import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto;
import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto.Factor;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una evaluacion de producto en borrador, lista para que el comite la apruebe.
 *
 * <p>Vive aparte porque la usan dos pruebas de casos de uso distintos —CU-47 la evalua,
 * CU-94 la aprueba— y duplicarla haria que las dos pudieran divergir sin que nadie se
 * entere.
 */
final class EscenarioDeProducto {

    private EscenarioDeProducto() {}

    /** Los cuatro factores obligatorios, con el riesgo alto ya controlado. */
    static List<RiesgoDelProducto.Riesgo> losCuatroFactores() {
        return List.of(
                new RiesgoDelProducto.Riesgo(Factor.CLIENTE, "Cliente sin historial verificable", 4, 4),
                new RiesgoDelProducto.Riesgo(Factor.PRODUCTO, "Aportes recurrentes en efectivo", 2, 3),
                new RiesgoDelProducto.Riesgo(Factor.CANAL, "Alta digital sin presencia fisica", 3, 2),
                new RiesgoDelProducto.Riesgo(Factor.GEOGRAFIA, "Frontera con jurisdiccion sensible", 2, 2));
    }

    static UUID enBorrador(BaseDeCumplimiento base, ContextoSesion ctx, LocalDate hoy) {
        BaseDeCumplimiento.dsl.execute("DELETE FROM catalogo.licencia_regulatoria");
        BaseDeCumplimiento.fixtura.licencia("OTORGADA", "[\"RETIRO\"]", hoy.plusYears(2));
        return BaseDeCumplimiento.transaccion
                .execute(t -> BaseDeCumplimiento.productoCU.evaluar(
                        new EntradaEvaluacion(
                                "producto-" + UUID.randomUUID().toString().substring(0, 8),
                                "RETIRO",
                                losCuatroFactores(),
                                Map.of(0, List.of("KYC reforzado")),
                                "[]",
                                "[]",
                                false),
                        ctx))
                .evaluacionId();
    }
}
