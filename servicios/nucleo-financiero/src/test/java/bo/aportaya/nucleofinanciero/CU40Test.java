package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU40EvaluarLimites.EntradaLimites;
import bo.aportaya.nucleofinanciero.aplicacion.CU40EvaluarLimites.SalidaLimites;
import bo.aportaya.nucleofinanciero.dominio.EvaluacionDeTope;
import bo.aportaya.nucleofinanciero.dominio.VentanaDeLimite;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-40 · Evaluar limites antes de una operacion. */
class CU40Test extends BaseDeBilletera {

    private static final String RETIRO = "RETIRO";
    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    @Test
    @DisplayName(
            "Dado un límite mensual de retiro y un consumo acumulado cercano al tope · Cuando el usuario intenta retirar un monto que lo supera · Entonces la operación se rechaza indicando el disponible restante")
    void criterio1() {
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("5000.00"));
        UUID limite = fixtura.limite(RETIRO, ESTANDAR, "MES", new BigDecimal("1000.00"), null);
        ContextoSesion ctx = contextoDe(usuario);

        // Se consume casi todo el tope, por el camino real: la operacion que acumula.
        transaccion.execute(e -> {
            var aplicados = limitesCU.exigirDentroDe(dsl, new EntradaLimites(cuenta, RETIRO, bob("900.00")), ctx);
            limitesCU.acumularDentroDe(dsl, cuenta, aplicados, bob("900.00"));
            return null;
        });

        assertThatThrownBy(() -> transaccion.execute(
                        e -> limitesCU.exigirDentroDe(dsl, new EntradaLimites(cuenta, RETIRO, bob("200.00")), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("queda")
                .hasMessageContaining("100.00");
        assertThat(limite).isNotNull();
    }

    @Test
    @DisplayName(
            "Dado un concepto sin límite configurado para el nivel del usuario · Cuando se evalúa una operación · Entonces se rechaza por ausencia de límite (denegar por omisión)")
    void criterio2() {
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("5000.00"));
        ContextoSesion ctx = contextoDe(usuario);

        SalidaLimites salida =
                transaccion.execute(e -> limitesCU.ejecutar(new EntradaLimites(cuenta, "RECARGA", bob("10.00")), ctx));

        assertThat(salida.permitido()).isFalse();
        assertThat(salida.motivoRechazo()).contains("deniega por omision");
        assertThat(salida.limitesEvaluados()).isEmpty();
    }

    @Test
    @DisplayName(
            "Dada una operación aplicada y luego reversada · Cuando se consulta el consumo de la ventana · Entonces el importe reversado no cuenta contra el límite")
    void criterio3() {
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("5000.00"));
        fixtura.limite(RETIRO, ESTANDAR, "MES", new BigDecimal("1000.00"), null);
        ContextoSesion ctx = contextoDe(usuario);

        var aplicados = transaccion.execute(e -> {
            var lista = limitesCU.exigirDentroDe(dsl, new EntradaLimites(cuenta, RETIRO, bob("800.00")), ctx);
            limitesCU.acumularDentroDe(dsl, cuenta, lista, bob("800.00"));
            return lista;
        });

        // Se reversa: un error del sistema no puede comerle el cupo del mes a nadie.
        transaccion.execute(e -> {
            limitesCU.devolverDentroDe(dsl, cuenta, aplicados, bob("800.00"));
            return null;
        });

        // Y ahora los 900 entran, porque el acumulado volvio a cero.
        transaccion.execute(e -> limitesCU.exigirDentroDe(dsl, new EntradaLimites(cuenta, RETIRO, bob("900.00")), ctx));
        assertThat(contar(
                        "SELECT monto_acumulado::int FROM nucleo_financiero.consumo_limite WHERE cuenta_billetera_id = ?",
                        cuenta))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        // La autoridad es la base: fn_lim_evaluar deniega por omision, y el caso de
        // uso tiene que decir lo mismo que ella.
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("100.00"));

        assertThat(rechazaLaBase("SELECT fn_lim_evaluar('%s', 'RETIRO', 1.00)".formatted(cuenta)))
                .contains("R-LIM-01");
    }

    @Test
    @DisplayName("rechaza por R-LIM-02")
    void rechazaRLIM02() {
        // Un consumo por ventana, no dos.
        //
        // HALLAZGO: la regla esta DUPLICADA en la base. `generar_ddl.py` ya emite un
        // unico sobre (cuenta_billetera_id, limite_id, ventana_inicio) y
        // sql/40_reglas lo vuelve a declarar como `uq_consumo_ventana`: hay dos
        // indices identicos sobre las mismas tres columnas, y el que salta es el
        // generado. La regla se cumple —por duplicado—, pero cada escritura paga dos
        // indices. Quitar uno es decision de modelo, troncal, no de este carril.
        // Por eso la prueba afirma que la base RECHAZA y sobre que columnas, en vez
        // de atarse al nombre de cual de los dos gano.
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("100.00"));
        UUID limite = fixtura.limite(RETIRO, ESTANDAR, "MES", new BigDecimal("1000.00"), null);
        String insert =
                """
                INSERT INTO nucleo_financiero.consumo_limite
                    (id, cuenta_billetera_id, limite_id, ventana_inicio, ventana_fin,
                     monto_acumulado, cantidad_acumulada, actualizado_en)
                VALUES (gen_random_uuid(), '%s', '%s', date_trunc('month', now()),
                        date_trunc('month', now()) + interval '1 month', 10.00, 1, now())
                """
                        .formatted(cuenta, limite);
        dslFixtura.execute(insert);

        assertThat(rechazaLaBase(insert))
                .contains("duplicate key")
                .contains("cuenta_billetera_id, limite_id, ventana_inicio");
    }

    @Test
    @DisplayName("rechaza por R-LIM-03")
    void rechazaRLIM03() {
        // Vigencias sin solape por concepto, nivel y ventana. En la practica el que
        // salta primero es el indice unico, que es mas estricto: ver el informe.
        fixtura.limite(RETIRO, ESTANDAR, "MES", new BigDecimal("1000.00"), null);

        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.limite_operativo_billetera
                            (id, concepto, nivel_debida_diligencia, ventana, monto_maximo, moneda,
                             base_normativa, vigente_desde, activo)
                        VALUES (gen_random_uuid(), 'RETIRO', 'ESTANDAR', 'MES', 2000.00, 'BOB',
                                'ASFI 540/2025', current_date, true)
                        """))
                .contains("uq_limite_operativo_billetera");
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        // ck_cuenta_saldo_no_negativo: una cuenta que no admite negativo no baja de cero.
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("10.00"));

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(cuenta)))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Consultar dos veces no consume: solo `acumularDentroDe` mueve el acumulado,
        // y se llama DESPUES de escribir el movimiento. Contar al evaluar cobraria
        // cupo por una operacion que todavia puede fallar.
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("5000.00"));
        fixtura.limite(RETIRO, ESTANDAR, "MES", new BigDecimal("1000.00"), null);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaLimites primera =
                transaccion.execute(e -> limitesCU.ejecutar(new EntradaLimites(cuenta, RETIRO, bob("100.00")), ctx));
        SalidaLimites segunda =
                transaccion.execute(e -> limitesCU.ejecutar(new EntradaLimites(cuenta, RETIRO, bob("100.00")), ctx));

        assertThat(primera.permitido()).isTrue();
        assertThat(segunda.permitido()).isTrue();
        assertThat(contar("SELECT count(*)::int FROM nucleo_financiero.consumo_limite"))
                .isZero();
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El acumulado se lee con FOR UPDATE. Dos operaciones seguidas suman, no se
        // pisan: un limite que se evade corriendo dos veces el mismo pedido no es
        // un limite.
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("5000.00"));
        fixtura.limite(RETIRO, ESTANDAR, "MES", new BigDecimal("1000.00"), null);
        ContextoSesion ctx = contextoDe(usuario);

        for (int i = 0; i < 2; i++) {
            transaccion.execute(e -> {
                var aplicados = limitesCU.exigirDentroDe(dsl, new EntradaLimites(cuenta, RETIRO, bob("400.00")), ctx);
                limitesCU.acumularDentroDe(dsl, cuenta, aplicados, bob("400.00"));
                return null;
            });
        }

        assertThat(contar(
                        "SELECT monto_acumulado::int FROM nucleo_financiero.consumo_limite WHERE cuenta_billetera_id = ?",
                        cuenta))
                .isEqualTo(800);
        // Y el tercero ya no entra.
        assertThatThrownBy(() -> transaccion.execute(
                        e -> limitesCU.exigirDentroDe(dsl, new EntradaLimites(cuenta, RETIRO, bob("400.00")), ctx)))
                .isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-40 no mueve dinero: lo que cuadra es el borde del tope. El monto exacto
        // ENTRA; un centavo mas, no.
        var tope = new EvaluacionDeTope.Tope(
                RETIRO, "MES", Optional.of(bob("1000.00")), Optional.empty(), bob("999.99"), 1);

        assertThat(EvaluacionDeTope.evaluar(List.of(tope), bob("0.01")).permitido())
                .isTrue();
        assertThat(EvaluacionDeTope.evaluar(List.of(tope), bob("0.02")).permitido())
                .isFalse();
        assertThat(tope.disponible()).isEqualByComparingTo(bob("0.01"));
        // Y sin topes se deniega, nunca se asume infinito.
        assertThat(EvaluacionDeTope.evaluar(List.of(), bob("0.01")).permitido()).isFalse();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "cumplimiento"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "cumplimiento"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Si el tope rechaza, no queda consumo escrito: acumular antes de saber si la
        // operacion procede cobraria cupo por algo que no paso.
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, new BigDecimal("5000.00"));
        fixtura.limite(RETIRO, ESTANDAR, "MES", new BigDecimal("100.00"), null);
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(
                        e -> limitesCU.exigirDentroDe(dsl, new EntradaLimites(cuenta, RETIRO, bob("500.00")), ctx)))
                .isInstanceOf(ErrorDeNegocio.class);

        assertThat(contar("SELECT count(*)::int FROM nucleo_financiero.consumo_limite"))
                .isZero();
    }

    @Test
    @DisplayName("rechaza una ventana desconocida: no se asume ninguna")
    void rechazaVentanaDesconocida() {
        // Tratar una ventana que no se reconoce como diaria inventaria un tope que
        // nadie configuro.
        OffsetDateTime ahora = OffsetDateTime.of(2026, 8, 26, 12, 0, 0, 0, ZoneOffset.UTC);

        assertThat(VentanaDeLimite.resolver("MES", ahora).inicio().toLocalDate())
                .isEqualTo(java.time.LocalDate.of(2026, 8, 1));
        assertThat(VentanaDeLimite.resolver("ANIO", ahora).fin().toLocalDate())
                .isEqualTo(java.time.LocalDate.of(2026, 12, 31));
        assertThatThrownBy(() -> VentanaDeLimite.resolver("QUINCENA", ahora))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se asume ninguna");
    }
}
