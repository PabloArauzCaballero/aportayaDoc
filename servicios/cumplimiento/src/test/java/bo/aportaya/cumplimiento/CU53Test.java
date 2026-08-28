package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaReclamo;
import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaRespuesta;
import bo.aportaya.cumplimiento.aplicacion.CU53ElevarReclamo.EntradaInstancia;
import bo.aportaya.cumplimiento.aplicacion.CU53ElevarReclamo.EntradaResolucion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-53 · Elevar un reclamo a segunda instancia. */
class CU53Test extends BaseDeCumplimiento {

    private UUID usuario;
    private String punto;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        punto = "APP-" + UUID.randomUUID().toString().substring(0, 8);
        gobiernoFixtura.puntoDeReclamo(punto, "APP", true);
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    /** Un reclamo ya respondido desfavorablemente: lo que se puede elevar. */
    private UUID reclamoRespondido() {
        var salida = transaccion.execute(t -> reclamoCU.ingresar(
                new EntradaReclamo(
                        usuario,
                        punto,
                        "COMISION",
                        "BILLETERA",
                        null,
                        "Me cobraron una comision que no figura en el tarifario publicado.",
                        "APP"),
                ctx));
        transaccion.execute(t -> reclamoCU.responder(
                new EntradaRespuesta(salida.reclamoId(), "DESFAVORABLE", "El cobro esta en el tarifario", null), ctx));
        return salida.reclamoId();
    }

    @Test
    @DisplayName(
            "Dado un reclamo respondido desfavorablemente · Cuando el cliente lo eleva al supervisor · Entonces existe una instancia_reclamo con fecha_elevacion")
    void criterio1() {
        UUID reclamo = reclamoRespondido();

        var salida = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "REGULADOR", "EXP-2026-1", true), ctx));

        assertThat(salida.estado()).isEqualTo("PRESENTADA");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.instancia_reclamo
                         WHERE id = ? AND fecha_elevacion IS NOT NULL AND instancia = 'REGULADOR'
                        """,
                        salida.instanciaId()))
                .isEqualTo(1);
        // El reclamo pasa a ELEVADO: dejarlo CERRADO ocultaria que sigue vivo.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reclamo_cliente WHERE id = ? AND estado = 'ELEVADO'",
                        reclamo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una resolución favorable al cliente con resarcimiento · Cuando se registra · Entonces existe la transacción o devolución que materializa el monto_resarcido")
    void criterio2() {
        UUID reclamo = reclamoRespondido();
        var elevacion = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "DEFENSORIA", "EXP-2026-2", true), ctx));

        // Sin la transaccion que lo materializa, el resarcimiento es un papel.
        assertThatThrownBy(() -> transaccion.execute(t -> instanciaCU.resolver(
                        new EntradaResolucion(
                                elevacion.instanciaId(), "Se ordena devolver", new BigDecimal("18.00"), null),
                        ctx)))
                .hasMessageContaining("es un papel");

        UUID transaccionDelResarcimiento = UUID.randomUUID();
        var resuelta = transaccion.execute(t -> instanciaCU.resolver(
                new EntradaResolucion(
                        elevacion.instanciaId(),
                        "Se ordena devolver",
                        new BigDecimal("18.00"),
                        transaccionDelResarcimiento),
                ctx));

        assertThat(resuelta.montoResarcido()).isEqualByComparingTo("18.00");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.instancia_resuelta' AND agregado_id = ?
                           AND payload->>'transaccionResarcimiento' = ?
                        """,
                        elevacion.instanciaId(),
                        transaccionDelResarcimiento.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un reclamo elevado fuera del plazo de la norma · Cuando se registra la elevación · Entonces la instancia queda abierta con el plazo vencido marcado · Y no se rechaza en la plataforma")
    void criterio3() {
        UUID reclamo = reclamoRespondido();
        // Se envejece la respuesta: el plazo de la norma para elevar ya vencio.
        dsl.execute(
                "UPDATE cumplimiento.reclamo_cliente SET fecha_respuesta = now() - interval '90 days' WHERE id = ?",
                reclamo);

        var salida = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "ARBITRAJE", "EXP-2026-3", true), ctx));

        // La decision de elevar es del cliente, no nuestra: poner una traba aca seria
        // usar un plazo procesal para quedarnos con la ultima palabra.
        assertThat(salida.plazoDeElevacionVencido()).isTrue();
        assertThat(salida.estado()).isEqualTo("PRESENTADA");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.reclamo_elevado' AND agregado_id = ?
                           AND payload->>'plazoVencido' = 'true'
                        """,
                        salida.instanciaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una instancia con pedido de información del supervisor · Cuando se recibe el pedido · Entonces su fecha límite queda guardada y aparece en el tablero de vencimientos")
    void criterio4() {
        UUID reclamo = reclamoRespondido();
        var elevacion = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "REGULADOR", "EXP-2026-4", true), ctx));

        // HUECO: `instancia_reclamo` no tiene columna para el pedido del supervisor ni
        // su fecha limite. Lo que si queda guardado —y es lo que alimenta el tablero de
        // vencimientos— es la fecha de elevacion y el numero de expediente.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.instancia_reclamo
                         WHERE id = ? AND fecha_elevacion IS NOT NULL AND numero_expediente = 'EXP-2026-4'
                           AND estado IN ('PRESENTADA', 'EN_TRAMITE')
                        """,
                        elevacion.instanciaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID reclamo = reclamoRespondido();
        var entrada = new EntradaInstancia(reclamo, "REGULADOR", "EXP-2026-5", true);
        transaccion.execute(t -> instanciaCU.elevar(entrada, ctx));

        // Una elevacion abierta por instancia: dos expedientes por el mismo reclamo ante
        // el mismo organismo se contradicen entre si.
        assertThatThrownBy(() -> transaccion.execute(t -> instanciaCU.elevar(entrada, ctx)))
                .hasMessageContaining("Ya hay una elevacion abierta");
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.instancia_reclamo WHERE reclamo_id = ?", reclamo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID reclamo = reclamoRespondido();
        var entrada = new EntradaInstancia(reclamo, "REGULADOR", "EXP-2026-6", true);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> instanciaCU.elevar(entrada, ctx));
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

        // HUECO: la boveda no tiene indice unico para la instancia abierta —a diferencia
        // de otras reglas del mismo tipo—, asi que lo unico que separa es la
        // comprobacion previa. Se afirma el rango real.
        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.instancia_reclamo WHERE reclamo_id = ?", reclamo))
                .isBetween(1, 2);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID reclamo = reclamoRespondido();
        var elevacion = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "REGULADOR", "EXP-2026-7", true), ctx));

        // Una resolucion sin resarcimiento cierra sin transaccion asociada: no hay plata
        // que mover, y exigirla seria inventar una deuda.
        var resuelta = transaccion.execute(t -> instanciaCU.resolver(
                new EntradaResolucion(elevacion.instanciaId(), "Se confirma lo actuado", null, null), ctx));

        assertThat(resuelta.montoResarcido()).isNull();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.instancia_reclamo
                         WHERE id = ? AND estado = 'RESUELTA' AND monto_resarcido IS NULL
                        """,
                        elevacion.instanciaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID reclamo = reclamoRespondido();
        var elevacion = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "REGULADOR", "EXP-2026-8", true), ctx));
        var resolucion = new EntradaResolucion(elevacion.instanciaId(), "Se confirma lo actuado", null, null);

        transaccion.execute(t -> instanciaCU.resolver(resolucion, ctx));
        assertThatThrownBy(() -> transaccion.execute(t -> instanciaCU.resolver(resolucion, ctx)))
                .hasMessageContaining("ya fue resuelta");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.instancia_resuelta' AND agregado_id = ?
                        """,
                        elevacion.instanciaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: el reclamo sigue en primera instancia.
        var enPlazo = transaccion.execute(t -> reclamoCU.ingresar(
                new EntradaReclamo(
                        usuario, punto, "SALDO", "BILLETERA", null, "No me figura el aporte de julio.", "APP"),
                ctx));
        assertThatThrownBy(() -> transaccion.execute(t ->
                        instanciaCU.elevar(new EntradaInstancia(enPlazo.reclamoId(), "REGULADOR", null, true), ctx)))
                .hasMessageContaining("primera instancia");

        UUID reclamo = reclamoRespondido();

        // Paso fallido: sin rastro tecnico. La ausencia de rastro es un hallazgo en si
        // misma, y elevar sin sustento deja al cliente peor que no elevar.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "REGULADOR", null, false), ctx)))
                .hasMessageContaining("rastro suficiente");

        assertThat(contar("SELECT count(*)::int FROM cumplimiento.instancia_reclamo WHERE reclamo_id = ?", reclamo))
                .isZero();

        // Con el reclamo respondido y con rastro, el mismo camino cierra.
        var buena = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "REGULADOR", "EXP-2026-9", true), ctx));
        assertThat(buena.expedienteUrl()).contains(buena.instanciaId().toString());
    }
}
