package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.erp.aplicacion.CU102AltaDeTercero.EntradaOrden;
import bo.aportaya.erp.aplicacion.CU102AltaDeTercero.EntradaTercero;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-102 · Dar de alta un tercero comercial y su orden de compra. */
class CU102Test extends BaseDeErp {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    private String nit;
    private UUID centroId;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        nit = "NIT-102-" + SECUENCIA.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 6);
        centroId = fixtura.centroDeCosto("CC102-" + UUID.randomUUID().toString().substring(0, 6), "AREA");
        ctx = contextoDe(fixtura.usuario());
    }

    private EntradaTercero alta(String tipo) {
        return new EntradaTercero(tipo, "Proveedor de prueba", nit, "proveedor@ejemplo.bo", null);
    }

    private EntradaOrden orden(UUID terceroId) {
        return new EntradaOrden(
                terceroId,
                centroId,
                "OC-" + UUID.randomUUID().toString().substring(0, 8),
                "Servicios de prueba del carril",
                new BigDecimal("8000.00"),
                "BOB");
    }

    @Test
    @DisplayName(
            "Dado un NIT que no existe en tercero_comercial · Cuando se da de alta un proveedor nuevo con ese NIT · Entonces se crea tercero_comercial en estado ACTIVO")
    void criterio1() {
        UUID id = transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM erp.tercero_comercial WHERE id = ? AND estado = 'ACTIVO' AND numero_documento = ?",
                        id,
                        nit))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.evento_dominio WHERE tipo = 'erp.tercero_dado_de_alta' AND agregado_id = ?",
                        id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una orden_compra en estado BORRADOR · Cuando Contabilidad la aprueba · Entonces pasa a estado APROBADA con aprobada_por registrado")
    void criterio2() {
        UUID tercero = transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));
        var creada = transaccion.execute(t -> terceroCU.crearOrden(orden(tercero), ctx));
        assertThat(creada.estado()).isEqualTo("BORRADOR");

        var aprobada = transaccion.execute(t -> terceroCU.aprobarOrden(creada.ordenId(), ctx));

        assertThat(aprobada.estado()).isEqualTo("APROBADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.orden_compra WHERE id = ? AND estado = 'APROBADA' AND aprobada_por IS NOT NULL",
                        creada.ordenId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un tercero_comercial en estado BLOQUEADO · Cuando se intenta crear una orden_compra nueva contra él · Entonces el sistema rechaza la operación")
    void criterio3() {
        UUID tercero = transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));
        dsl.execute("UPDATE erp.tercero_comercial SET estado = 'BLOQUEADO' WHERE id = ?", tercero);

        // Bloquearlo y seguir comprandole convierte el bloqueo en una nota de color.
        assertThatThrownBy(() -> transaccion.execute(t -> terceroCU.crearOrden(orden(tercero), ctx)))
                .hasMessageContaining("BLOQUEADO");
        assertThat(contar("SELECT count(*)::int FROM erp.orden_compra WHERE tercero_comercial_id = ?", tercero))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID a = transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));
        UUID b = transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));

        // El mismo NIT cargado dos veces permite pagarle dos veces a la misma empresa:
        // el reintento devuelve el que hay.
        assertThat(b).isEqualTo(a);
        assertThat(contar("SELECT count(*)::int FROM erp.tercero_comercial WHERE numero_documento = ?", nit))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID tercero = transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));
        var creada = transaccion.execute(t -> terceroCU.crearOrden(orden(tercero), ctx));

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> terceroCU.aprobarOrden(creada.ordenId(), ctx));
            } catch (Exception e) {
                errores.add(e);
            }
        };
        var uno = new Thread(intento);
        var dos = new Thread(intento);
        uno.start();
        dos.start();
        uno.join();
        dos.join();

        assertThat(errores).hasSize(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.evento_dominio WHERE tipo = 'erp.orden_compra_aprobada' AND agregado_id = ?",
                        creada.ordenId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID tercero = transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));
        var creada = transaccion.execute(t -> terceroCU.crearOrden(orden(tercero), ctx));
        var aprobada = transaccion.execute(t -> terceroCU.aprobarOrden(creada.ordenId(), ctx));

        // El monto no cambia al aprobar: aprobar es firmar lo que dice la orden, no
        // renegociarla.
        assertThat(aprobada.monto()).isEqualByComparingTo(creada.monto());
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.orden_compra WHERE id = ? AND monto_total = 8000.00",
                        creada.ordenId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));
        transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));
        transaccion.execute(t -> terceroCU.darDeAlta(alta("PROVEEDOR"), ctx));

        assertThat(contar("SELECT count(*)::int FROM erp.tercero_comercial WHERE numero_documento = ?", nit))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.tercero_dado_de_alta' AND payload->>'numeroDocumento' = ?
                        """,
                        nit))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: emitir una orden contra un cliente. No se le compra a quien nos
        // compra: la cuenta contable y el circuito son otros.
        UUID cliente = transaccion.execute(t -> terceroCU.darDeAlta(alta("CLIENTE"), ctx));
        assertThatThrownBy(() -> transaccion.execute(t -> terceroCU.crearOrden(orden(cliente), ctx)))
                .hasMessageContaining("es cliente");

        // Paso fallido: aprobar una orden que no existe.
        assertThatThrownBy(() -> transaccion.execute(t -> terceroCU.aprobarOrden(UUID.randomUUID(), ctx)))
                .hasMessageContaining("no existe");
        assertThat(contar("SELECT count(*)::int FROM erp.orden_compra WHERE tercero_comercial_id = ?", cliente))
                .isZero();
    }
}
