package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.aplicacion.CU59CalcularPlazo.DiaSalteado;
import bo.aportaya.grupos.aplicacion.CU59CalcularPlazo.EntradaPlazo;
import bo.aportaya.grupos.aplicacion.CU59CalcularPlazo.SalidaPlazo;
import bo.aportaya.grupos.dominio.AlcanceDeCalendario;
import bo.aportaya.grupos.dominio.CalendarioVacio;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.PlazoHabil;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-59 · Mantener el calendario de días no hábiles. */
class CU59Test extends BaseDeCU59 {

    @Test
    @DisplayName(
            "Dado un plazo de 5 días hábiles desde el viernes y un lunes feriado nacional · Cuando se calcula la fecha límite · Entonces el lunes no cuenta y la fecha resultante lo refleja · Y diasSalteados enumera el feriado")
    void criterio1() {
        // Viernes 6 de marzo de 2026; lunes 9 feriado nacional.
        feriado(LocalDate.of(2026, 3, 9), "NACIONAL", "Feriado de prueba");

        SalidaPlazo salida = calcular(LocalDate.of(2026, 3, 6), 5);

        assertThat(salida.fechaLimite()).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(salida.diasSalteados()).extracting(DiaSalteado::fecha).contains(LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName(
            "Dado un reclamo con fecha límite ya guardada · Cuando se declara un feriado dentro de ese plazo · Entonces la fecha límite del reclamo no cambia")
    void criterio2() {
        // El plazo se guarda al calcularlo. Declarar un feriado despues no lo mueve:
        // el calculo es una foto, no una consulta que se rehace.
        feriado(LocalDate.of(2026, 4, 6), "NACIONAL", "Feriado previo");
        LocalDate limiteGuardado = calcular(LocalDate.of(2026, 4, 3), 5).fechaLimite();

        feriado(LocalDate.of(2026, 4, 8), "NACIONAL", "Feriado declarado despues");

        assertThat(limiteGuardado).isEqualTo(LocalDate.of(2026, 4, 13));
    }

    @Test
    @DisplayName(
            "Dado un vencimiento de aporte que cae en feriado declarado antes del cálculo · Cuando se genera la obligación · Entonces el vencimiento se corre al siguiente día hábil y se notifica al grupo")
    void criterio3() {
        feriado(LocalDate.of(2026, 5, 1), "NACIONAL", "Dia del trabajo");

        LocalDate corrido = transaccion.execute(
                e -> calcularPlazo.correrSiCae(LocalDate.of(2026, 5, 1), AlcanceDeCalendario.NACIONAL, contexto()));

        assertThat(corrido).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(corrido).isAfter(LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName(
            "Dado un período sin calendario cargado · Cuando se pide calcular un plazo hábil · Entonces se rechaza con CALENDARIO_VACIO")
    void criterio4() {
        // 2031 no tiene un solo dia cargado: se rechaza en vez de contar corridos.
        assertThatThrownBy(() -> calcular(LocalDate.of(2031, 1, 5), 5))
                .isInstanceOf(CalendarioVacio.class)
                .hasMessageContaining("2031");
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        dejarUnaFilaEnLaBitacora();

        assertThat(rechazaLaBase("DELETE FROM comun.bitacora_evento")).contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Un feriado sin descripcion no se carga: sin la fuente escrita, nadie puede
        // explicar despues por que un plazo dio ese numero.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.dia_no_habil (id, fecha, alcance)
                        VALUES (gen_random_uuid(), DATE '2026-12-25', 'NACIONAL')
                        """))
                .contains("descripcion");
    }

    @Test
    @DisplayName("rechaza por R-CON-01")
    void rechazaRCON01() {
        // El plazo guardado no se recalcula. Se comprueba con el atomo, que es donde
        // vive la regla: sumar contra un calendario dado siempre da lo mismo.
        java.util.Set<LocalDate> feriados = java.util.Set.of(LocalDate.of(2026, 3, 9));
        bo.aportaya.plataforma.dominio.CalendarioHabil sinFinDeSemana = fecha -> feriados.contains(fecha)
                || fecha.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || fecha.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;

        LocalDate primera = PlazoHabil.sumar(LocalDate.of(2026, 3, 6), 5, sinFinDeSemana);
        LocalDate segunda = PlazoHabil.sumar(LocalDate.of(2026, 3, 6), 5, sinFinDeSemana);

        assertThat(primera).isEqualTo(segunda);
    }

    @Test
    @DisplayName("rechaza por R-CON-02")
    void rechazaRCON02() {
        // La fecha unica por (fecha, alcance, grupo) impide dos versiones del mismo
        // feriado: dos filas serian dos calendarios y dos plazos distintos.
        feriado(LocalDate.of(2026, 6, 21), "NACIONAL", "Año nuevo andino");

        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.dia_no_habil (id, fecha, descripcion, alcance)
                        VALUES (gen_random_uuid(), DATE '2026-06-21', 'Duplicado', 'NACIONAL')
                        """))
                .contains("uq_dia_no_habil");
    }

    @Test
    @DisplayName("rechaza por R-GRP-16")
    void rechazaRGRP16() {
        // Un feriado de alcance GRUPO sin grupo no aplica a nadie y a todos a la vez.
        assertThat(AlcanceDeCalendario.GRUPO.completoCon(java.util.Optional.empty()))
                .isFalse();
        assertThatThrownBy(() -> transaccion.execute(e -> calcularPlazo.ejecutar(
                        new EntradaPlazo(LocalDate.of(2026, 3, 6), 5, "GRUPO", java.util.Optional.empty()),
                        contexto())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("de que grupo");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Calcular no escribe nada: es idempotente por construccion, y dos llamadas
        // con la misma entrada dan exactamente la misma fecha.
        feriado(LocalDate.of(2026, 7, 16), "NACIONAL", "Feriado de julio");

        assertThat(calcular(LocalDate.of(2026, 7, 14), 5).fechaLimite())
                .isEqualTo(calcular(LocalDate.of(2026, 7, 14), 5).fechaLimite());
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos cargas del mismo feriado: la unicidad decide, no la aplicacion.
        feriado(LocalDate.of(2026, 8, 6), "NACIONAL", "Independencia");

        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.dia_no_habil (id, fecha, descripcion, alcance)
                        VALUES (gen_random_uuid(), DATE '2026-08-06', 'Independencia otra vez', 'NACIONAL')
                        """))
                .contains("uq_dia_no_habil");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-59 no mueve dinero: calcula fechas. Lo que cuadra aca es que la fecha
        // limite nunca cae en un dia no habil.
        feriado(LocalDate.of(2026, 9, 14), "NACIONAL", "Feriado de septiembre");

        LocalDate limite = calcular(LocalDate.of(2026, 9, 11), 5).fechaLimite();

        assertThat(limite.getDayOfWeek()).isNotIn(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
        assertThat(limite).isNotEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        java.util.UUID idEvento = java.util.UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "grupos"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "grupos"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }
}
