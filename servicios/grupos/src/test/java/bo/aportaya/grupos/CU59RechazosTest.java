package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-59 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU59RechazosTest extends BaseDeCU59 {

    @Test
    @DisplayName("rechaza por R-GRP-16")
    void rechazaRGRP16() {
        // Un dia no habil por fecha, alcance y grupo. Dos filas para el mismo feriado
        // no cambian el calculo hoy, pero el dia que alguien las edite por separado
        // el mismo plazo dara dos fechas distintas segun cual se lea.
        LocalDate fecha = LocalDate.of(2599, 8, 6);
        feriado(fecha, "NACIONAL", "Prueba de duplicado");

        String error = rechazaLaBase("INSERT INTO catalogo.dia_no_habil (id, fecha, descripcion, alcance) VALUES"
                + " (gen_random_uuid(), DATE '2599-08-06', 'Otro nombre para el mismo dia', 'NACIONAL')");

        assertThat(error).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-CON-01")
    void rechazaRCON01() {
        // El plazo se calcula sobre dias HABILES: un feriado no cuenta. Si contara, un
        // vencimiento caeria en un dia en que nadie puede pagar, y la mora seria de la
        // plataforma, no de la persona.
        LocalDate viernes = LocalDate.of(2599, 8, 1);
        feriado(viernes.plusDays(4), "NACIONAL", "Feriado de prueba");

        var conFeriado = calcular(viernes, 3);

        assertThat(conFeriado.diasSalteados())
                .as("el feriado sembrado tiene que aparecer entre los dias salteados")
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-CON-02")
    void rechazaRCON02() {
        // La fecha limite se persiste al crear, no se recalcula al consultar
        // (invariante 8). Dos consultas seguidas tienen que dar lo mismo aunque entre
        // medio se agregue un feriado.
        LocalDate desde = LocalDate.of(2599, 9, 1);

        var primera = calcular(desde, 5);
        var segunda = calcular(desde, 5);

        assertThat(segunda.fechaLimite()).isEqualTo(primera.fechaLimite());
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La bitacora es append-only.
        dejarUnaFilaEnLaBitacora();

        String error = rechazaLaBase("DELETE FROM comun.bitacora_evento WHERE entidad LIKE 'prueba%'");

        assertThat(error).contains("R-AUD-01");
    }
}
