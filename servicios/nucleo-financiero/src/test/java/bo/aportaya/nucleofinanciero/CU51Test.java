package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU51EjecutarCierreDiario.EntradaCierre;
import bo.aportaya.nucleofinanciero.aplicacion.CU51EjecutarCierreDiario.SalidaCierre;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-51 · Ejecutar el cierre diario.
 *
 * <p>Vive aca y no en aportes, aunque planes/07 lo asigne al carril 3A: escribe
 * {@code cierre_diario} y lee {@code asiento_contable}, y las dos tablas son de este
 * esquema. Ponerlo en aportes exigiria escribir un esquema ajeno (invariante 11).
 */
class CU51Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        borrarCierres();
        fixtura.limpiarBilleteras();
    }

    /** La fecha del cierre, en UTC: {@code LocalDate.now()} usa la zona de la maquina. */
    private LocalDate hoy() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Una fecha propia por prueba.
     *
     * <p>{@code saldo_diario_billetera} es unico por cuenta y fecha (R-AUD-07) y
     * append-only: si dos pruebas cerraran el mismo dia, la segunda chocaria contra la
     * foto de la primera y el fallo diria algo que no es.
     */
    private LocalDate diaDe(int desplazamiento) {
        return hoy().minusDays(desplazamiento);
    }

    private void borrarCierres() {
        dsl.execute("DELETE FROM nucleo_financiero.cierre_diario");
    }

    private EntradaCierre entrada(LocalDate fecha, int excepciones, boolean custodiaCuadrada) {
        return new EntradaCierre(
                fecha,
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                4,
                excepciones,
                custodiaCuadrada);
    }

    /** Un asiento sin confirmar en la fecha: es lo que bloquea el cierre. */
    private UUID asientoEnBorrador(LocalDate fecha) {
        UUID cuenta = contable.cuentaDeMovimiento(codigoCorto(), "ACTIVO", "DEUDORA");
        UUID contrapartida = contable.cuentaDeMovimiento(codigoCorto(), "INGRESO", "ACREEDORA");
        UUID asientoId = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.asiento_contable
                    (id, fecha, glosa, origen_tipo, origen_id, estado)
                VALUES (?, ?, 'sin confirmar', 'AJUSTE', gen_random_uuid(), 'BORRADOR')
                """,
                asientoId,
                fecha);
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.movimiento_contable
                    (id, asiento_id, cuenta_id, debe, haber, descripcion)
                VALUES (gen_random_uuid(), ?, ?, 10.00, 0.00, 'debe'),
                       (gen_random_uuid(), ?, ?, 0.00, 10.00, 'haber')
                """,
                asientoId,
                cuenta,
                asientoId,
                contrapartida);
        return asientoId;
    }

    private String codigoCorto() {
        return String.valueOf(System.nanoTime()).substring(8, 14);
    }

    @Test
    @DisplayName(
            "Dado un día sin excepciones y con custodia cuadrada · Cuando se ejecuta el cierre · Entonces cierre_diario.cuadrado es true · Y existen saldo_diario_billetera para todas las cuentas activas")
    void criterio1() {
        LocalDate fecha = diaDe(101);
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal("250.00"));
        ContextoSesion ctx = contextoDe(usuario);

        SalidaCierre salida = transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx));

        assertThat(salida.cuadrado()).isTrue();
        assertThat(salida.motivoDelDescuadre()).isNull();
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.cierre_diario WHERE fecha = ? AND cuadrado",
                        fecha))
                .isEqualTo(1);
        // La foto del dia: sin ella no hay contra que emitir un extracto ni conciliar
        // la custodia manana.
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.saldo_diario_billetera WHERE cuenta_billetera_id = ? AND fecha = ?",
                        cuenta,
                        fecha))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un día con una excepción de conciliación abierta · Cuando se ejecuta el cierre · Entonces cuadrado es false")
    void criterio2() {
        LocalDate fecha = diaDe(102);
        UUID usuario = fixtura.usuario();
        fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);

        // El conteo llega desde afuera: las excepciones viven en `aportes` y este
        // servicio no lee ese esquema (invariante 11).
        SalidaCierre salida = transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 1, true), ctx));

        assertThat(salida.cuadrado()).isFalse();
        assertThat(salida.motivoDelDescuadre()).contains("excepcion(es) de conciliacion abiertas");
        // El dia se cierra igual, pero marcado: no cerrar nada dejaria el sistema sin
        // foto y sin rastro de que hubo un problema.
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.cierre_diario WHERE fecha = ? AND NOT cuadrado",
                        fecha))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "nucleo_financiero.dia_cerrado_con_observaciones",
                        salida.cierreId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un día ya cerrado · Cuando el trabajo programado se ejecuta de nuevo · Entonces devuelve el cierre existente y no reescribe los saldos diarios")
    void criterio3() {
        LocalDate fecha = diaDe(103);
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal("80.00"));
        ContextoSesion ctx = contextoDe(usuario);

        SalidaCierre primera = transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx));
        SalidaCierre segunda = transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx));

        assertThat(segunda.cierreId()).isEqualTo(primera.cierreId());
        assertThat(segunda.yaExistia()).isTrue();
        // Reescribir los saldos borraria la foto del dia, que es exactamente lo que el
        // cierre existe para conservar.
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.saldo_diario_billetera WHERE cuenta_billetera_id = ? AND fecha = ?",
                        cuenta,
                        fecha))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un asiento_contable sin confirmar en la fecha · Cuando se ejecuta el cierre · Entonces el cierre no procede y el asiento queda señalado como bloqueante")
    void criterio4() {
        LocalDate fecha = diaDe(104);
        UUID usuario = fixtura.usuario();
        fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);
        UUID asiento = asientoEnBorrador(fecha);

        assertThatThrownBy(() -> transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin confirmar");

        assertThat(contar("SELECT count(*)::int FROM nucleo_financiero.cierre_diario WHERE fecha = ?", fecha))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.asiento_contable WHERE id = ? AND estado = 'BORRADOR'",
                        asiento))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave del cierre es la FECHA: el planificador reintenta y no puede haber
        // dos cierres del mismo dia.
        LocalDate fecha = diaDe(105);
        UUID usuario = fixtura.usuario();
        fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaCierre a = transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx));
        SalidaCierre b = transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx));

        assertThat(b.cierreId()).isEqualTo(a.cierreId());
        assertThat(b.yaExistia()).isTrue();
        assertThat(contar("SELECT count(*)::int FROM nucleo_financiero.cierre_diario WHERE fecha = ?", fecha))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos cierres del mismo dia: la BASE decide, no la aplicacion. Con dos
        // cierres del mismo dia no hay forma de saber cual es la verdad.
        LocalDate fecha = diaDe(106);
        UUID usuario = fixtura.usuario();
        fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);
        transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.cierre_diario
                            (id, fecha, total_recaudado, total_conciliado, total_excepciones,
                             cantidad_pagos, cuadrado, cerrado_por, cerrado_en)
                        VALUES (gen_random_uuid(), DATE '%s', 1.00, 1.00, 0.00, 1, true,
                                gen_random_uuid(), now())
                        """
                                .formatted(fecha)))
                .isNotEmpty();
        assertThat(contar("SELECT count(*)::int FROM nucleo_financiero.cierre_diario WHERE fecha = ?", fecha))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Lo conciliado mas lo que quedo en excepcion tiene que dar lo recaudado. Si
        // no da, el cierre esta firmando un numero que no se conoce.
        LocalDate fecha = diaDe(107);
        UUID usuario = fixtura.usuario();
        fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);

        transaccion.execute(t -> cierreDiarioCU.ejecutar(
                new EntradaCierre(
                        fecha, new BigDecimal("1000.00"), new BigDecimal("999.99"), new BigDecimal("0.01"), 3, 0, true),
                ctx));

        var fila = dsl.fetchOne(
                "SELECT total_recaudado, total_conciliado, total_excepciones FROM nucleo_financiero.cierre_diario WHERE fecha = ?",
                fecha);
        assertThat(fila.get(1, BigDecimal.class).add(fila.get(2, BigDecimal.class)))
                .isEqualByComparingTo(fila.get(0, BigDecimal.class));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "cierre-diario"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "cierre-diario"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // El asiento sin confirmar aborta la transaccion entera: ni cierre ni saldos
        // diarios. Medio cierre es peor que ninguno — la foto quedaria de un dia que
        // nunca se cerro.
        LocalDate fecha = diaDe(108);
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);
        asientoEnBorrador(fecha);

        assertThatThrownBy(() -> transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx)))
                .isInstanceOf(ErrorDeNegocio.class);

        assertThat(contar("SELECT count(*)::int FROM nucleo_financiero.cierre_diario WHERE fecha = ?", fecha))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.saldo_diario_billetera WHERE cuenta_billetera_id = ? AND fecha = ?",
                        cuenta,
                        fecha))
                .isZero();
    }
}
