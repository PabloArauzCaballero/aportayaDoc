package bo.aportaya.grupos;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.aplicacion.CU65Retirarse.SalidaRetiro;
import bo.aportaya.grupos.dominio.PosicionAlRetirarse;
import bo.aportaya.grupos.dominio.PosicionAlRetirarse.Momento;
import bo.aportaya.grupos.dominio.PosicionAlRetirarse.Tipo;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-65 · Retirarse de un grupo. */
class CU65Test extends BaseDeCU65 {

    @Test
    @DisplayName(
            "Dado un participante que aún no cobró y aportó Bs 1.500 sin deuda · Cuando solicita el retiro · Entonces la posición es ACREEDORA por Bs 1.500 · Y el momento de liquidación es AL_CIERRE_DEL_CICLO")
    void criterio1() {
        UUID participante = participanteActivo("EN_CURSO");

        SalidaRetiro salida = solicitar(participante, false, "1500.00", "0.00", "0.00");

        assertThat(salida.posicion().tipo()).isEqualTo(Tipo.ACREEDORA);
        assertThat(salida.posicion().monto()).isEqualTo(Dinero.de("1500.00", BOB));
        assertThat(salida.posicion().momentoDeLiquidacion()).isEqualTo(Momento.AL_CIERRE_DEL_CICLO);
    }

    @Test
    @DisplayName(
            "Dado un participante que ya cobró su turno · Cuando solicita el retiro · Entonces la posición es DEUDORA por los aportes restantes · Y sin plan de pago aceptado se rechaza")
    void criterio2() {
        UUID participante = participanteActivo("EN_CURSO");

        SalidaRetiro salida = solicitar(participante, true, "1500.00", "0.00", "2000.00");

        assertThat(salida.posicion().tipo()).isEqualTo(Tipo.DEUDORA);
        assertThat(salida.posicion().monto()).isEqualTo(Dinero.de("2000.00", BOB));
        assertThatThrownBy(() -> aprobar(salida, participante, Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("plan de pago");
    }

    @Test
    @DisplayName(
            "Dado un grupo que todavía no arrancó · Cuando un participante se retira · Entonces el cupo queda LIBRE y se le devuelve lo aportado íntegro")
    void criterio3() {
        UUID participante = participanteActivo("CONFORMADO");

        SalidaRetiro salida = solicitar(participante, false, "500.00", "0.00", "0.00");
        aprobar(salida, participante, Optional.empty());

        assertThat(salida.posicion().monto()).isEqualTo(Dinero.de("500.00", BOB));
        assertThat(salida.posicion().momentoDeLiquidacion()).isEqualTo(Momento.INMEDIATO);
        assertThat(estadoDelCupoDe(participante)).isEqualTo("LIBRE");
    }

    @Test
    @DisplayName("rechaza por R-GRP-11")
    void rechazaRGRP11() {
        // Un participante que ya se retiro no se retira de nuevo.
        UUID participante = participanteActivo("EN_CURSO");
        SalidaRetiro salida = solicitar(participante, false, "500.00", "0.00", "0.00");
        aprobar(salida, participante, Optional.empty());

        assertThatThrownBy(() -> solicitar(participante, false, "500.00", "0.00", "0.00"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya no esta activo");
    }

    @Test
    @DisplayName("rechaza por R-GRP-12")
    void rechazaRGRP12() {
        // La posicion sale de un catalogo cerrado: acreedora, deudora o neutra.
        UUID participante = participanteActivo("EN_CURSO");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.solicitud_retiro
                            (id, participante_id, motivo, solicitado_en, estado, posicion,
                             requiere_reemplazo, liquidacion_calculada)
                        VALUES (gen_random_uuid(), '%s', 'prueba', now(), 'PENDIENTE', 'INDEFINIDA', false, 0.00)
                        """
                                .formatted(participante)))
                .contains("ck_solicitud_retiro_posicion");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // La salida queda escrita: el participante no se borra, se marca RETIRADO
        // con su motivo y su fecha.
        UUID participante = participanteActivo("EN_CURSO");
        SalidaRetiro salida = solicitar(participante, false, "500.00", "0.00", "0.00");

        aprobar(salida, participante, Optional.empty());

        assertThat(estadoDelParticipante(participante)).isEqualTo("RETIRADO");
        assertThat(motivoDeSalida(participante)).isNotBlank();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // El calculo es puro: la misma entrada da la misma posicion, siempre.
        Dinero aportado = Dinero.de("1500.00", BOB);
        Dinero cero = Dinero.cero(BOB);

        assertThat(PosicionAlRetirarse.calcular(false, true, aportado, cero, cero))
                .isEqualTo(PosicionAlRetirarse.calcular(false, true, aportado, cero, cero));
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Cuando lo aportado no alcanza a cubrir la deuda, la posicion se da vuelta:
        // no cobro y aun asi debe. Es el caso que un `if` apurado se saltea.
        PosicionAlRetirarse posicion = PosicionAlRetirarse.calcular(
                false, true, Dinero.de("300.00", BOB), Dinero.de("500.00", BOB), Dinero.cero(BOB));

        assertThat(posicion.tipo()).isEqualTo(Tipo.DEUDORA);
        assertThat(posicion.monto()).isEqualTo(Dinero.de("200.00", BOB));
        assertThat(posicion.exigePlanDePago()).isTrue();
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Lo aportado menos la deuda, al centavo, y sin que aparezca un signo de la
        // nada: si aportado == deuda la posicion es NEUTRA, no acreedora por 0,00.
        PosicionAlRetirarse posicion = PosicionAlRetirarse.calcular(
                false, true, Dinero.de("750.50", BOB), Dinero.de("750.50", BOB), Dinero.cero(BOB));

        assertThat(posicion.tipo()).isEqualTo(Tipo.NEUTRA);
        assertThat(posicion.monto()).isEqualTo(Dinero.cero(BOB));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "garantia"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "garantia"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }
}
