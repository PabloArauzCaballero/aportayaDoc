package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU40EvaluarLimites.EntradaLimite;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-40 · Evaluar limites antes de una operacion. */
class CU40Test extends BaseDeCumplimiento {

    private UUID usuario;
    private UUID cuenta;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        cuenta = uif.cuentaBilletera(usuario, "BOB");
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        dsl.execute("DELETE FROM nucleo_financiero.consumo_limite");
        dsl.execute("DELETE FROM catalogo.limite_operativo_billetera WHERE concepto LIKE 'RETIRO%'");
    }

    @Test
    @DisplayName(
            "Dado un límite mensual de retiro y un consumo acumulado cercano al tope · Cuando el usuario intenta retirar un monto que lo supera · Entonces la operación se rechaza indicando el disponible restante")
    void criterio1() {
        UUID limite = gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        gobiernoFixtura.consumo(cuenta, limite, "4200.00", 8);

        var veredicto = transaccion.execute(
                t -> limiteCU.evaluar(new EntradaLimite(cuenta, "RETIRO", new BigDecimal("3000.00")), ctx));

        assertThat(veredicto.permitido()).isFalse();
        // El mensaje dice CUANTO queda: decirle solo «excede el limite» lo deja
        // intentando cinco veces sin saber con cuanto si puede.
        assertThat(veredicto.motivoRechazo()).contains("800.00");
        assertThat(veredicto.limitesEvaluados()).hasSize(1);
        assertThat(veredicto.limitesEvaluados().get(0).disponible()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName(
            "Dado un concepto sin límite configurado para el nivel del usuario · Cuando se evalúa una operación · Entonces se rechaza por ausencia de límite (denegar por omisión)")
    void criterio2() {
        // Permitir lo no configurado significa que un olvido en el catalogo abre la
        // puerta de par en par, y no se descubre hasta que alguien lo aprovecha.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> limiteCU.evaluar(new EntradaLimite(cuenta, "RETIRO", new BigDecimal("10.00")), ctx)))
                .hasMessageContaining("deniega por omision");
    }

    @Test
    @DisplayName(
            "Dada una operación aplicada y luego reversada · Cuando se consulta el consumo de la ventana · Entonces el importe reversado no cuenta contra el límite")
    void criterio3() {
        UUID limite = gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        gobiernoFixtura.consumo(cuenta, limite, "4200.00", 8);

        // La reversa la aplica el nucleo financiero sobre su propio consumo: aca se
        // comprueba que, una vez descontada, el disponible vuelve a alcanzar. Contar un
        // importe reversado contra el limite castigaria a alguien por un error nuestro.
        dsl.execute(
                """
                UPDATE nucleo_financiero.consumo_limite
                   SET monto_acumulado = monto_acumulado - 3000.00, cantidad_acumulada = cantidad_acumulada - 1
                 WHERE cuenta_billetera_id = ? AND limite_id = ?
                """,
                cuenta,
                limite);

        var veredicto = transaccion.execute(
                t -> limiteCU.evaluar(new EntradaLimite(cuenta, "RETIRO", new BigDecimal("3000.00")), ctx));

        assertThat(veredicto.permitido()).isTrue();
        assertThat(veredicto.limitesEvaluados().get(0).consumido()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID limite = gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        gobiernoFixtura.consumo(cuenta, limite, "1000.00", 2);
        var entrada = new EntradaLimite(cuenta, "RETIRO", new BigDecimal("500.00"));

        var a = transaccion.execute(t -> limiteCU.evaluar(entrada, ctx));
        var b = transaccion.execute(t -> limiteCU.evaluar(entrada, ctx));

        // Evaluar NO descuenta: este servicio decide, el nucleo financiero descuenta al
        // aplicar. Evaluar dos veces da lo mismo dos veces.
        assertThat(b.permitido()).isEqualTo(a.permitido());
        assertThat(b.limitesEvaluados().get(0).consumido())
                .isEqualByComparingTo(a.limitesEvaluados().get(0).consumido());
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.consumo_limite WHERE cuenta_billetera_id = ?",
                        cuenta))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID limite = gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        gobiernoFixtura.consumo(cuenta, limite, "4500.00", 9);
        var entrada = new EntradaLimite(cuenta, "RETIRO", new BigDecimal("400.00"));

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var resultados = java.util.Collections.synchronizedList(new java.util.ArrayList<Boolean>());
        Runnable intento = () -> {
            try {
                barrera.await();
                resultados.add(
                        transaccion.execute(t -> limiteCU.evaluar(entrada, ctx)).permitido());
            } catch (Exception e) {
                resultados.add(false);
            }
        };
        var uno = new Thread(intento);
        var dos = new Thread(intento);
        uno.start();
        dos.start();
        uno.join();
        dos.join();

        // Las dos evaluaciones ven el mismo consumo y las dos permiten: **evaluar no
        // reserva**. Quien impide que las dos se apliquen es el nucleo financiero, con
        // el FOR UPDATE de fn_lim_evaluar al descontar. Se afirma lo que este servicio
        // garantiza, no lo que garantiza el otro.
        assertThat(resultados).hasSize(2);
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.consumo_limite WHERE cuenta_billetera_id = ?",
                        cuenta))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID limite = gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        gobiernoFixtura.consumo(cuenta, limite, "1800.00", 3);

        var veredicto = transaccion.execute(
                t -> limiteCU.evaluar(new EntradaLimite(cuenta, "RETIRO", new BigDecimal("100.00")), ctx));

        // Tope = consumido + disponible, al centavo. Si no cuadrara, el disponible que
        // se le muestra al usuario seria una cifra inventada.
        var evaluado = veredicto.limitesEvaluados().get(0);
        assertThat(evaluado.consumido().add(evaluado.disponible())).isEqualByComparingTo(evaluado.tope());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID limite = gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        gobiernoFixtura.consumo(cuenta, limite, "1000.00", 2);
        var entrada = new EntradaLimite(cuenta, "RETIRO", new BigDecimal("500.00"));

        transaccion.execute(t -> limiteCU.evaluar(entrada, ctx));
        transaccion.execute(t -> limiteCU.evaluar(entrada, ctx));

        // Cada evaluacion deja su rastro: sin el, nadie puede reconstruir despues por
        // que una operacion se dejo pasar. Dos evaluaciones son dos hechos distintos.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.limites_evaluados' AND agregado_id = ?
                        """,
                        cuenta))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: la cuenta no existe.
        assertThatThrownBy(() -> transaccion.execute(t -> limiteCU.evaluar(
                        new EntradaLimite(UUID.randomUUID(), "RETIRO", new BigDecimal("100.00")), ctx)))
                .hasMessageContaining("no existe");

        // Paso fallido: sin limite configurado. Y no deja consumo escrito, porque este
        // servicio no escribe consumo.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> limiteCU.evaluar(new EntradaLimite(cuenta, "RETIRO", new BigDecimal("100.00")), ctx)))
                .hasMessageContaining("deniega por omision");
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.consumo_limite WHERE cuenta_billetera_id = ?",
                        cuenta))
                .isZero();

        // Con el limite configurado, el mismo camino cierra.
        gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        var veredicto = transaccion.execute(
                t -> limiteCU.evaluar(new EntradaLimite(cuenta, "RETIRO", new BigDecimal("100.00")), ctx));
        assertThat(veredicto.permitido()).isTrue();
    }
}
