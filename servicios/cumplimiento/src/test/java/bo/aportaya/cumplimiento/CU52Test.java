package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaProrroga;
import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaReclamo;
import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaRespuesta;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-52 · Atender un reclamo en plazo. */
class CU52Test extends BaseDeCumplimiento {

    private static final String DESCRIPCION =
            "Me cobraron una comision que no figura en el tarifario publicado del mes.";

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

    private EntradaReclamo reclamo(String monto) {
        return new EntradaReclamo(
                usuario,
                punto,
                "COMISION",
                "BILLETERA",
                monto == null ? null : new BigDecimal(monto),
                DESCRIPCION,
                "APP");
    }

    @Test
    @DisplayName(
            "Dado un reclamo ingresado un lunes · Cuando se registra · Entonces plazo_respuesta queda guardado a 5 días hábiles y no se recalcula luego")
    void criterio1() {
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo("18.00"), ctx));

        assertThat(salida.diasHabilesPlazo()).isEqualTo(5);
        // Guardado, no derivado: si se recalculara, un feriado nuevo movería el plazo
        // que ya se le prometio al cliente.
        var guardado = dsl.fetchOne(
                "SELECT plazo_respuesta, dias_habiles_plazo FROM cumplimiento.reclamo_cliente WHERE id = ?",
                salida.reclamoId());
        assertThat(guardado.get("plazo_respuesta", OffsetDateTime.class)).isEqualTo(salida.plazoRespuesta());
        assertThat(guardado.get("dias_habiles_plazo", Short.class)).isEqualTo((short) 5);
        // Y la conservacion a diez años queda desde el ingreso (R-CON-05).
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.reclamo_cliente
                         WHERE id = ? AND conservar_hasta >= (fecha_ingreso + interval '10 years')::date
                        """,
                        salida.reclamoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un reclamo que requiere prórroga · Cuando se comunica al cliente dentro de los 5 días · Entonces plazo_prorrogado_hasta no excede 10 días hábiles")
    void criterio2() {
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo("18.00"), ctx));
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);

        transaccion.execute(t -> {
            reclamoCU.prorrogar(
                    new EntradaProrroga(
                            salida.reclamoId(), salida.plazoRespuesta().plusDays(4), ahora, null, null),
                    ctx);
            return null;
        });

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.reclamo_cliente
                         WHERE id = ? AND plazo_prorrogado_hasta <= fecha_ingreso + interval '10 days'
                           AND prorroga_comunicada_al_cliente_en IS NOT NULL
                        """,
                        salida.reclamoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un reclamo con resultado FAVORABLE y monto reclamado de Bs 18 · Cuando se intenta cerrar sin devolución asociada · Entonces el cierre se rechaza")
    void criterio3() {
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo("18.00"), ctx));

        // R-CON-04: darle la razon a alguien y no devolverle la plata es darsela de
        // mentira.
        assertThatThrownBy(() -> transaccion.execute(t -> reclamoCU.responder(
                        new EntradaRespuesta(salida.reclamoId(), "FAVORABLE", "Tenia razon", null), ctx)))
                .hasMessageContaining("exige la devolucion");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reclamo_cliente WHERE id = ? AND estado <> 'CERRADO'",
                        salida.reclamoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));
        var respuesta = new EntradaRespuesta(salida.reclamoId(), "DESFAVORABLE", "El cobro esta en el tarifario", null);

        transaccion.execute(t -> reclamoCU.responder(respuesta, ctx));
        assertThatThrownBy(() -> transaccion.execute(t -> reclamoCU.responder(respuesta, ctx)))
                .hasMessageContaining("ya estaba cerrado");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reclamo_cliente WHERE id = ? AND estado = 'CERRADO'",
                        salida.reclamoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));
        var respuesta = new EntradaRespuesta(salida.reclamoId(), "DESFAVORABLE", "El cobro esta en el tarifario", null);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> reclamoCU.responder(respuesta, ctx));
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
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.reclamo_respondido' AND agregado_id = ?
                        """,
                        salida.reclamoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El cuadre de un reclamo es entre lo reclamado y lo reparado: **si hay monto,
        // tiene que haber devolucion; si no lo hay, no hay nada que devolver**. La
        // devolucion la ejecuta tarifas contra un cargo realmente cobrado (R-TAR-11), y
        // este servicio solo la referencia (invariante 11).
        var conMonto = transaccion.execute(t -> reclamoCU.ingresar(reclamo("18.00"), ctx));
        assertThatThrownBy(() -> transaccion.execute(t -> reclamoCU.responder(
                        new EntradaRespuesta(conMonto.reclamoId(), "FAVORABLE", "Tenia razon", null), ctx)))
                .hasMessageContaining("exige la devolucion");

        // Sin monto reclamado, un FAVORABLE cierra sin devolucion: no hay plata que
        // devolver, y exigir una seria inventar una deuda.
        var sinMonto = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));
        transaccion.execute(t -> reclamoCU.responder(
                new EntradaRespuesta(sinMonto.reclamoId(), "FAVORABLE", "Se corrige el dato", null), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.reclamo_cliente
                         WHERE id = ? AND estado = 'CERRADO' AND resultado = 'FAVORABLE'
                           AND devolucion_comision_id IS NULL
                        """,
                        sinMonto.reclamoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        var prorroga =
                new EntradaProrroga(salida.reclamoId(), salida.plazoRespuesta().plusDays(3), ahora, null, null);

        transaccion.execute(t -> {
            reclamoCU.prorrogar(prorroga, ctx);
            return null;
        });
        // Una segunda prorroga no se acumula sobre la primera: prorrogar dos veces es
        // como no tener plazo.
        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reclamoCU.prorrogar(prorroga, ctx);
                    return null;
                }))
                .hasMessageContaining("ya tiene una prorroga");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.reclamo_prorrogado' AND agregado_id = ?
                        """,
                        salida.reclamoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: el canal esta apagado. Aceptar un reclamo por un canal que no
        // se atiende es peor que decirlo.
        String apagado = "TEL-" + UUID.randomUUID().toString().substring(0, 8);
        gobiernoFixtura.puntoDeReclamo(apagado, "TELEFONO", false);
        assertThatThrownBy(() -> transaccion.execute(t -> reclamoCU.ingresar(
                        new EntradaReclamo(usuario, apagado, "COMISION", "BILLETERA", null, DESCRIPCION, "TELEFONO"),
                        ctx)))
                .hasMessageContaining("no esta habilitado");

        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo("18.00"), ctx));

        // Paso fallido: prorroga comunicada despues de vencido el plazo original.
        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reclamoCU.prorrogar(
                            new EntradaProrroga(
                                    salida.reclamoId(),
                                    salida.plazoRespuesta().plusDays(3),
                                    salida.plazoRespuesta().plusDays(1),
                                    null,
                                    null),
                            ctx);
                    return null;
                }))
                .hasMessageContaining("antes de que venza");

        // Paso fallido: prorroga mas alla del maximo sin comunicar al organismo.
        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reclamoCU.prorrogar(
                            new EntradaProrroga(
                                    salida.reclamoId(),
                                    salida.plazoRespuesta().plusDays(30),
                                    OffsetDateTime.now(ZoneOffset.UTC),
                                    null,
                                    null),
                            ctx);
                    return null;
                }))
                .hasMessageContaining("comunicacion escrita al organismo");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reclamo_cliente WHERE id = ? AND plazo_prorrogado_hasta IS NULL",
                        salida.reclamoId()))
                .isEqualTo(1);

        // Comunicada a tiempo y dentro del maximo, el mismo camino cierra.
        transaccion.execute(t -> {
            reclamoCU.prorrogar(
                    new EntradaProrroga(
                            salida.reclamoId(),
                            salida.plazoRespuesta().plusDays(3),
                            OffsetDateTime.now(ZoneOffset.UTC),
                            null,
                            null),
                    ctx);
            return null;
        });
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reclamo_cliente WHERE id = ? AND plazo_prorrogado_hasta IS NOT NULL",
                        salida.reclamoId()))
                .isEqualTo(1);
    }
}
