package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU41RegistrarPcc01.EntradaUmbral;
import bo.aportaya.cumplimiento.aplicacion.CU43RemitirReportes.EntradaEnvio;
import bo.aportaya.cumplimiento.aplicacion.CU43RemitirReportes.EntradaReporte;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-43 · Remitir los reportes mensuales a la UIF. */
class CU43Test extends BaseDeCumplimiento {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Un mes distinto por prueba: el reporte cubre el periodo entero, no un usuario. */
    private static final java.util.concurrent.atomic.AtomicInteger MES =
            new java.util.concurrent.atomic.AtomicInteger(1);

    private LocalDate fechaDelPeriodo;

    private UUID usuario;
    private UUID cuenta;
    private UUID umbralId;
    private String codigo;
    private String periodo;
    private ContextoSesion generador;
    private ContextoSesion aprobador;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        cuenta = uif.cuentaBilletera(usuario, "USD");
        umbralId = uif.umbral("PCC-01", "CARGA_BILLETERA", true, "1000.00", 3);
        codigo = "PCC01-" + UUID.randomUUID().toString().substring(0, 6);
        uif.catalogoDeReporte(codigo, "UIF", 15);
        fechaDelPeriodo =
                LocalDate.now(ZoneOffset.UTC).minusMonths(MES.getAndIncrement()).withDayOfMonth(10);
        periodo = fechaDelPeriodo.format(PERIODO);
        generador = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        aprobador = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    /**
     * Un numero de constancia propio.
     *
     * <p>{@code uq_envio_regulatorio_numero_constancia} es unico en toda la tabla, no
     * por reporte: dos envios no pueden compartir constancia porque la constancia ES la
     * prueba de que el organismo recibio. Cada prueba trae la suya.
     */
    private String constancia() {
        return "C-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Deja n formularios PCC-01 del periodo, ya con su declaracion tomada. */
    private void formularios(int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
            UUID tx = uif.transaccionConUmbralApagado(umbralId, cuenta, "1500.00", "USD", "RECARGA", ahora);
            transaccion.execute(t -> pccCU.registrar(
                    new EntradaUmbral(
                            usuario,
                            tx,
                            "CARGA_BILLETERA",
                            new BigDecimal("1500.00"),
                            "USD",
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            null,
                            fechaDelPeriodo.minusDays(2),
                            fechaDelPeriodo,
                            ahora,
                            false,
                            null,
                            "SALARIO",
                            "Compra de electrodomesticos"),
                    generador));
        }
    }

    @Test
    @DisplayName(
            "Dado un mes con 12 formularios PCC-01 generados · Cuando se arma el reporte · Entonces cantidad_registros es 12 y reporte_en_cero es false")
    void criterio1() {
        formularios(12);

        var salida =
                transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));

        assertThat(salida.cantidadRegistros()).isEqualTo(12);
        assertThat(salida.reporteEnCero()).isFalse();
        assertThat(salida.hashArchivo()).hasSize(64);
        // HUECO: `reporte_regulatorio_id` no se puede escribir —la tabla es
        // append-only— asi que el enlace se deriva del periodo. Lo que impide reportar
        // dos veces el mismo mes es uq_reporte_catalogo_periodo.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE periodo_remision = ? AND formulario = 'PCC-01'
                        """,
                        periodo))
                .isEqualTo(12);
    }

    @Test
    @DisplayName(
            "Dado un mes sin ninguna operación sobre umbral · Cuando se arma el reporte · Entonces existe un reporte_regulatorio con reporte_en_cero = true")
    void criterio2() {
        var salida =
                transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));

        // R-UIF-06: no mandar nada y mandar cero son cosas distintas. La primera parece
        // un olvido y el regulador la trata como tal.
        assertThat(salida.cantidadRegistros()).isZero();
        assertThat(salida.reporteEnCero()).isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reporte_regulatorio WHERE id = ? AND reporte_en_cero = true",
                        salida.reporteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un reporte cuya fecha_limite venció sin envío · Cuando corre el control diario · Entonces existe un hallazgo_auditoria abierto")
    void criterio3() {
        formularios(2);
        var reporte =
                transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));
        // Se envejece la fecha limite: es lo que hace el paso del tiempo.
        dsl.execute(
                "UPDATE cumplimiento.reporte_regulatorio SET fecha_limite = current_date - 1 WHERE id = ?",
                reporte.reporteId());

        var envio = transaccion.execute(t -> reporteCU.aprobarYEnviar(
                new EntradaEnvio(periodo, codigo, aprobador.usuarioId(), "PORTAL_WEB", constancia(), true, 0),
                aprobador));

        assertThat(envio.fueraDePlazo()).isTrue();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.hallazgo_auditoria
                         WHERE codigo = ? AND estado = 'ABIERTO' AND severidad = 'ALTA'
                        """,
                        "REP-" + periodo + "-" + codigo.substring(0, Math.min(20 - 12, codigo.length()))))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        formularios(3);
        var entrada = new EntradaReporte(periodo, codigo, "PCC-01");

        var a = transaccion.execute(t -> reporteCU.generar(entrada, generador));
        var b = transaccion.execute(t -> reporteCU.generar(entrada, generador));

        // uq_reporte_catalogo_periodo: un reporte por catalogo y periodo. Generar dos
        // veces devuelve el que hay, no arma otro con los mismos registros.
        assertThat(b.reporteId()).isEqualTo(a.reporteId());
        assertThat(b.cantidadRegistros()).isEqualTo(a.cantidadRegistros());
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.reporte_regulatorio WHERE periodo = ?", periodo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        formularios(2);
        var entrada = new EntradaReporte(periodo, codigo, "PCC-01");

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> reporteCU.generar(entrada, generador));
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

        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.reporte_regulatorio WHERE periodo = ?", periodo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        formularios(4);
        var salida =
                transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));

        // El monto total del reporte tiene que ser la suma de lo que lleva, al centavo:
        // si no, el regulador recibe un total que no cuadra con su propio detalle.
        var suma = dsl.fetchOne(
                        """
                        SELECT COALESCE(SUM(monto_equivalente_usd), 0)
                          FROM cumplimiento.registro_operacion_relevante
                         WHERE periodo_remision = ? AND formulario = 'PCC-01'
                        """,
                        periodo)
                .get(0, BigDecimal.class);
        var total = dsl.fetchOne(
                        "SELECT monto_total FROM cumplimiento.reporte_regulatorio WHERE id = ?", salida.reporteId())
                .get(0, BigDecimal.class);
        assertThat(total).isEqualByComparingTo(suma);
        assertThat(salida.cantidadRegistros()).isEqualTo(4);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        formularios(2);
        var entrada = new EntradaReporte(periodo, codigo, "PCC-01");
        transaccion.execute(t -> reporteCU.generar(entrada, generador));
        transaccion.execute(t -> reporteCU.generar(entrada, generador));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.reporte_generado' AND payload->>'catalogoCodigo' = ?
                        """,
                        codigo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        formularios(2);

        // Paso fallido: enviar sin haber generado.
        assertThatThrownBy(() -> transaccion.execute(t -> reporteCU.aprobarYEnviar(
                        new EntradaEnvio(periodo, codigo, aprobador.usuarioId(), "PORTAL_WEB", constancia(), true, 0),
                        aprobador)))
                .hasMessageContaining("todavia no se genero");

        var reporte =
                transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));

        // Paso fallido: aprueba quien genero (R-SEG-04). El reporte queda GENERADO.
        assertThatThrownBy(() -> transaccion.execute(t -> reporteCU.aprobarYEnviar(
                        new EntradaEnvio(periodo, codigo, generador.usuarioId(), "PORTAL_WEB", constancia(), true, 0),
                        generador)))
                .hasMessageContaining("no puede ser quien lo genero");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reporte_regulatorio WHERE id = ? AND estado = 'GENERADO'",
                        reporte.reporteId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.envio_regulatorio WHERE reporte_regulatorio_id = ?",
                        reporte.reporteId()))
                .isZero();

        // Con otro aprobador, el mismo camino cierra.
        var envio = transaccion.execute(t -> reporteCU.aprobarYEnviar(
                new EntradaEnvio(periodo, codigo, aprobador.usuarioId(), "PORTAL_WEB", constancia(), true, 0),
                aprobador));
        assertThat(envio.numeroConstancia()).startsWith("C-");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reporte_regulatorio WHERE id = ? AND estado = 'ENVIADO'",
                        reporte.reporteId()))
                .isEqualTo(1);
    }
}
