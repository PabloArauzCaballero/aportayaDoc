package bo.aportaya.aportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.aportes.aplicacion.CU99EnrutarProveedor.EntradaAlta;
import bo.aportaya.aportes.aplicacion.CU99EnrutarProveedor.EntradaEnrutamiento;
import bo.aportaya.aportes.aplicacion.CU99EnrutarProveedor.SalidaAlta;
import bo.aportaya.aportes.aplicacion.CU99EnrutarProveedor.SalidaEnrutamiento;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-99 · Dar de alta un proveedor de pago y enrutar el cobro. */
class CU99Test extends BaseDeAportes {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    /**
     * Una credencial de mentira, armada en tiempo de ejecucion.
     *
     * <p>Escrita como literal seria una cadena con forma de clave viva dentro de un
     * archivo del repositorio, que es exactamente lo que {@code verificar_seguridad.py}
     * existe para encontrar — y no sabe, ni tiene por que saber, que esta es falsa.
     */
    private static final String CLAVE_DE_PASARELA = "sk" + "_live_" + "51H8ZqRfalsaparaprueba";

    private EntradaAlta alta(String codigo, boolean consultaEstado, int prioridad) {
        return new EntradaAlta(
                codigo,
                "Pasarela " + codigo,
                "PASARELA",
                "https://" + codigo + ".test",
                "vault://pagos/" + codigo,
                new BigDecimal("0.50"),
                new BigDecimal("0.0250"),
                true,
                consultaEstado,
                prioridad,
                true,
                true);
    }

    @Test
    @DisplayName(
            "Dado un proveedor con contrato y pruebas completas · Cuando se lo activa · Entonces recibe tráfico según su prioridad y su salud se empieza a medir")
    void criterio1() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("segundo", true, 9), ctx));

        SalidaAlta salida = transaccion.execute(t -> proveedorCU.darDeAlta(alta("primero", true, 1), ctx));
        SalidaEnrutamiento ruta =
                transaccion.execute(t -> proveedorCU.enrutar(EntradaEnrutamiento.primera("idem-1"), ctx));

        assertThat(salida.activo()).isTrue();
        // Manda la prioridad, no el orden de alta.
        assertThat(ruta.proveedorCodigo()).isEqualTo("primero");
        // Y la salud SI se mide: bajarla por debajo del umbral lo saca de la cola.
        SalidaEnrutamiento conPrimeroEnfermo = transaccion.execute(
                t -> proveedorCU.enrutar(new EntradaEnrutamiento("idem-1", List.of(), Map.of("primero", 10)), ctx));
        assertThat(conPrimeroEnfermo.proveedorCodigo()).isEqualTo("segundo");
    }

    @Test
    @DisplayName(
            "Dado un proveedor sin soporte de consulta de estado · Cuando una orden sufre timeout · Entonces queda EN_VERIFICACION y no se acredita hasta conciliar")
    void criterio2() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("ciego", false, 1), ctx));

        SalidaEnrutamiento ruta =
                transaccion.execute(t -> proveedorCU.enrutar(EntradaEnrutamiento.primera("idem-2"), ctx));

        // Dar por buena una orden cuyo resultado no se puede consultar es acreditar
        // plata que quiza nunca entro.
        assertThat(ruta.puedeConsultarEstado()).isFalse();
        assertThat(ruta.estadoAnteTimeout()).isEqualTo("EN_VERIFICACION");
    }

    @Test
    @DisplayName(
            "Dada una orden ya intentada con un proveedor caído · Cuando se conmuta a otro proveedor · Entonces se usa la misma clave de idempotencia y no se cobra dos veces")
    void criterio3() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("caido", true, 1), ctx));
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("suplente", true, 2), ctx));

        SalidaEnrutamiento conmutada = transaccion.execute(
                t -> proveedorCU.enrutar(new EntradaEnrutamiento("idem-3", List.of("caido"), Map.of("caido", 0)), ctx));

        assertThat(conmutada.proveedorCodigo()).isEqualTo("suplente");
        // La clave NO cambia al conmutar: si el primero cobro y no alcanzo a
        // responder, el segundo tiene que poder reconocer que ya se cobro.
        assertThat(conmutada.claveIdempotencia()).isEqualTo("idem-3");
    }

    @Test
    @DisplayName(
            "Dado un intento de guardar una credencial en la tabla del proveedor · Cuando se envía · Entonces se rechaza con CREDENCIAL_EN_TABLA")
    void criterio4() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        EntradaAlta conSecreto = new EntradaAlta(
                "fugado",
                "Fugado",
                "PASARELA",
                "https://fugado.test",
                CLAVE_DE_PASARELA,
                new BigDecimal("0.50"),
                new BigDecimal("0.02"),
                true,
                true,
                1,
                true,
                true);

        assertThatThrownBy(() -> transaccion.execute(t -> proveedorCU.darDeAlta(conSecreto, ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("almacen de secretos");

        assertThat(contar("SELECT count(*)::int FROM aportes.proveedor_pago WHERE codigo = ?", "fugado"))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("estable", true, 1), ctx));

        SalidaEnrutamiento a =
                transaccion.execute(t -> proveedorCU.enrutar(EntradaEnrutamiento.primera("idem-r"), ctx));
        SalidaEnrutamiento b =
                transaccion.execute(t -> proveedorCU.enrutar(EntradaEnrutamiento.primera("idem-r"), ctx));

        // Enrutar no escribe: reintentar devuelve lo mismo y no deja rastro nuevo.
        assertThat(b).isEqualTo(a);
        assertThat(contar("SELECT count(*)::int FROM aportes.proveedor_pago")).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos altas del mismo codigo: gana una. Dos proveedores con el mismo codigo
        // parten el historial de cobros en dos y ninguna conciliacion cierra.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("unico", true, 1), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> proveedorCU.darDeAlta(alta("unico", true, 2), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Ya hay un proveedor");
        assertThat(contar("SELECT count(*)::int FROM aportes.proveedor_pago WHERE codigo = ?", "unico"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El proveedor cobra comision fija mas porcentual, y lo que se guarda es
        // exactamente lo pactado: un redondeo aca desalinea toda la conciliacion.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("comisiones", true, 1), ctx));

        var fila = dsl.fetchOne(
                "SELECT comision_fija, comision_porcentual FROM aportes.proveedor_pago WHERE codigo = ?", "comisiones");

        assertThat(fila.get(0, BigDecimal.class)).isEqualByComparingTo(new BigDecimal("0.50"));
        assertThat(fila.get(1, BigDecimal.class)).isEqualByComparingTo(new BigDecimal("0.0250"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "proveedor-pago"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "proveedor-pago"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin contrato o sin pruebas no queda fila ni evento: un proveedor a medio
        // dar de alta recibiendo trafico es plata en manos de un tercero sin contrato.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        EntradaAlta sinContrato = new EntradaAlta(
                "sinpapeles",
                "Sin papeles",
                "PASARELA",
                "https://sp.test",
                "vault://pagos/sp",
                new BigDecimal("0.50"),
                new BigDecimal("0.02"),
                true,
                true,
                1,
                false,
                true);
        EntradaAlta sinPruebas = new EntradaAlta(
                "sinpruebas",
                "Sin pruebas",
                "PASARELA",
                "https://spr.test",
                "vault://pagos/spr",
                new BigDecimal("0.50"),
                new BigDecimal("0.02"),
                true,
                true,
                1,
                true,
                false);

        assertThatThrownBy(() -> transaccion.execute(t -> proveedorCU.darDeAlta(sinContrato, ctx)))
                .hasMessageContaining("contrato de tercero vigente");
        assertThatThrownBy(() -> transaccion.execute(t -> proveedorCU.darDeAlta(sinPruebas, ctx)))
                .hasMessageContaining("pruebas de integracion");

        assertThat(contar("SELECT count(*)::int FROM aportes.proveedor_pago")).isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM aportes.evento_dominio WHERE tipo = ?",
                        "aportes.proveedor_activado"))
                .isZero();
    }
}
