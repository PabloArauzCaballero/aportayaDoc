package bo.aportaya.erp;

import bo.aportaya.erp.aplicacion.CU103FacturaDeProveedor.EntradaFactura;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;

/**
 * El escenario que comparten las pruebas de CU-103: un ejercicio abierto, un proveedor
 * y dos personas distintas — quien aprueba y quien paga.
 *
 * <p>Son dos a proposito: R-CTB-05 exige que no sean la misma, y un escenario con un
 * solo usuario no podria ni siquiera montar el caso feliz.
 */
abstract class EscenarioDeFactura extends BaseDeErp {

    private static final AtomicInteger ANIO = new AtomicInteger(2300);

    protected int anio;
    protected UUID ejercicioId;
    protected UUID proveedor;
    protected ContextoSesion aprobador;
    protected ContextoSesion tesoreria;

    @BeforeEach
    void escenario() {
        anio = ANIO.incrementAndGet();
        aprobador = contextoDe(fixtura.usuario());
        tesoreria = contextoDe(fixtura.usuario());
        ejercicioId = transaccion
                .execute(t -> periodoCU.abrirEjercicio(anio, aprobador))
                .ejercicioId();
        proveedor = fixtura.tercero("PROVEEDOR", "NIT-103-" + anio);
    }

    protected LocalDate enPeriodo(int mes) {
        return dsl.fetchOne(
                        "SELECT fecha_inicio FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ? AND mes = ?",
                        ejercicioId,
                        (short) mes)
                .get(0, LocalDate.class);
    }

    protected UUID idDelPeriodo(int mes) {
        return dsl.fetchOne(
                        "SELECT id FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ? AND mes = ?",
                        ejercicioId,
                        (short) mes)
                .get(0, UUID.class);
    }

    /**
     * Una factura ya aprobada.
     *
     * <p>La aprobacion va en el alta y no despues: {@code factura_proveedor} es
     * append-only, asi que {@code aprobada_por} no se puede completar mas tarde. Queda
     * declarado como hueco del carril.
     */
    protected EntradaFactura factura(String monto, LocalDate emision) {
        return factura(monto, emision, aprobador.usuarioId());
    }

    protected EntradaFactura factura(String monto, LocalDate emision, UUID aprobadaPor) {
        return factura("F-" + UUID.randomUUID().toString().substring(0, 8), monto, emision, aprobadaPor);
    }

    protected EntradaFactura factura(String numero, String monto, LocalDate emision, UUID aprobadaPor) {
        return new EntradaFactura(
                proveedor,
                null,
                null,
                numero,
                emision,
                emision.plusDays(30),
                new BigDecimal(monto),
                "BOB",
                null,
                aprobadaPor);
    }
}
