package bo.aportaya.cumplimiento;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-06 · Revisión periódica de conocimiento del cliente
 *
 * Generado por scripts/nuevo_cu.py. NACE EN ROJO a propósito: el carril no decide
 * qué probar, decide cómo hacer pasar lo que la bóveda ya dejó escrito.
 *
 * Corre contra PostgreSQL 16 real (Testcontainers). Base en memoria NO: el modelo
 * usa EXCLUDE, btree_gist, RLS y numeric; una base que no los tiene prueba otro
 * sistema.
 */
class CU06Test {

    // --- criterios de aceptación de la bóveda ---------------------------
    // Uno por escenario gherkin, con el MISMO nombre. Si borrás uno, el gate
    // scripts/verificar_criterios.py falla: el criterio quedaría sin cubrir.
    @Test
    @DisplayName(
            "Dado un cliente de riesgo ALTO con periodicidad de 6 meses · Cuando pasan 6 meses desde su última calificación · Entonces existe una revision_periodica_kyc programada")
    void criterio1() {
        fail("CU-06 criterio 1 sin implementar");
    }

    @Test
    @DisplayName(
            "Dado un cliente cuyo monto observado supera en 300% al declarado · Cuando corre la revisión · Entonces existe un desvio_perfil con severidad alta · Y se genera una alerta_monitoreo_lft")
    void criterio2() {
        fail("CU-06 criterio 2 sin implementar");
    }

    @Test
    @DisplayName(
            "Dado una revisión vencida y no ejecutada · Cuando pasa el plazo de gracia · Entonces la cuenta_billetera queda en estado LIMITADA")
    void criterio3() {
        fail("CU-06 criterio 3 sin implementar");
    }

    // --- prueba de RECHAZO por cada restricción citada -------------------
    // No basta con que la aplicación valide: hay que probar que la BASE
    // rechaza. Un doble siempre acepta; por eso van contra PostgreSQL real.
    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        fail("R-AUD-04: falta la prueba de rechazo");
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        fail("R-LIM-01: falta la prueba de rechazo");
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        fail("R-UIF-09: falta la prueba de rechazo");
    }

    @Test
    @DisplayName("rechaza por R-UIF-11")
    void rechazaRUIF11() {
        fail("R-UIF-11: falta la prueba de rechazo");
    }

    // --- las obligatorias de este caso de uso ----------------------------
    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        fail("CU-06: falta la prueba de reintento");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        fail("CU-06: falta la prueba de concurrencia");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        fail("CU-06: falta la prueba de cuadre");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        fail("CU-06: falta la prueba de evento duplicado");
    }
}
