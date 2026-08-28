package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.erp.aplicacion.CU102AltaDeTercero.EntradaOrden;
import bo.aportaya.erp.aplicacion.CU102AltaDeTercero.EntradaTercero;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-102 · Lo que la base y el caso de uso rechazan. */
class CU102RechazosTest extends BaseDeErp {

    private String sufijo;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        sufijo = UUID.randomUUID().toString().substring(0, 8);
        ctx = contextoDe(fixtura.usuario());
    }

    @Test
    @DisplayName("rechaza por R-CTB-04")
    void rechazaRCTB04() {
        // Un tercero por documento: dos filas con el mismo NIT son dos historias de
        // compra del mismo proveedor, y ninguna de las dos completa. El caso de uso
        // devuelve el que ya hay — un reintento no debe crear un duplicado — y la base
        // frena el alta directa.
        String nit = "NIT-102R-" + sufijo;
        UUID primero = transaccion.execute(
                t -> terceroCU.darDeAlta(new EntradaTercero("PROVEEDOR", "Proveedor " + sufijo, nit, null, null), ctx));
        UUID reintento = transaccion.execute(
                t -> terceroCU.darDeAlta(new EntradaTercero("PROVEEDOR", "Otro " + sufijo, nit, null, null), ctx));

        assertThat(reintento).isEqualTo(primero);
        assertThat(contar("SELECT count(*)::int FROM erp.tercero_comercial WHERE numero_documento = ?", nit))
                .isEqualTo(1);
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.tercero_comercial (tipo, razon_social, numero_documento, estado)
                        VALUES ('PROVEEDOR', 'Clon', ?, 'ACTIVO')
                        """,
                        nit))
                .contains("uq_tercero_comercial_numero_documento");

        // Y una orden aprobada exige quien la aprobo: sin firma no hay compromiso de
        // gasto que se le pueda atribuir a alguien.
        UUID otro = fixtura.tercero("PROVEEDOR", "NIT-102R2-" + sufijo);
        UUID centro = fixtura.centroDeCosto("CC102R-" + sufijo, "AREA");
        var orden = transaccion.execute(t -> terceroCU.crearOrden(
                new EntradaOrden(otro, centro, "OC-" + sufijo, "Insumos", new BigDecimal("900.00"), "BOB"), ctx));

        assertThat(rechazaLaBase(
                        "UPDATE erp.orden_compra SET estado = 'APROBADA', aprobada_por = NULL WHERE id = ?",
                        orden.ordenId()))
                .contains("ck_orden_compra_aprobacion");
    }
}
