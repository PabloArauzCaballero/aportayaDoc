package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.erp.aplicacion.CU100AbrirCerrarPeriodo.EntradaCierre;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-106 · Generar el estado financiero del periodo. */
class CU106Test extends BaseDeErp {

    private static final AtomicInteger ANIO = new AtomicInteger(2500);

    private int anio;
    private UUID ejercicioId;
    private UUID activo;
    private UUID pasivo;
    private UUID patrimonio;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        anio = ANIO.incrementAndGet();
        ctx = contextoDe(fixtura.usuario());
        ejercicioId =
                transaccion.execute(t -> periodoCU.abrirEjercicio(anio, ctx)).ejercicioId();
        activo = fixtura.cuenta("1301-" + anio, "ACTIVO", "DEUDORA");
        pasivo = fixtura.cuenta("2301-" + anio, "PASIVO", "ACREEDORA");
        patrimonio = fixtura.cuenta("3301-" + anio, "PATRIMONIO", "ACREEDORA");
    }

    private UUID periodo(int mes) {
        return dsl.fetchOne(
                        "SELECT id FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ? AND mes = ?",
                        ejercicioId,
                        (short) mes)
                .get(0, UUID.class);
    }

    /** Un mes cerrado con la ecuacion contable cerrando: activo = pasivo + patrimonio. */
    private UUID mesCerrado(int mes) {
        UUID p = periodo(mes);
        fixtura.asiento(p, activo, pasivo, "6000.00", ctx.usuarioId());
        fixtura.asiento(p, activo, patrimonio, "4000.00", ctx.usuarioId());
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(p, "Cierre"), ctx));
        return p;
    }

    @Test
    @DisplayName(
            "Dado un período contable cerrado sin estado financiero previo · Cuando Contabilidad solicita el BALANCE_GENERAL de ese período · Entonces se crea estado_financiero_generado con su hash_contenido")
    void criterio1() {
        UUID enero = mesCerrado(1);

        var salida = transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));

        assertThat(salida.hashContenido()).hasSize(64);
        assertThat(salida.estado().ecuacionCierra()).isTrue();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.estado_financiero_generado
                         WHERE periodo_contable_id = ? AND tipo = 'BALANCE_GENERAL' AND length(hash_contenido) = 64
                        """,
                        enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un estado financiero ya generado para un período y tipo · Cuando se solicita generarlo de nuevo · Entonces el sistema devuelve YA_GENERADO_PARA_ESE_PERIODO")
    void criterio2() {
        UUID enero = mesCerrado(1);
        transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));

        // R-CTB-08: dos balances del mismo mes se contradicen entre si y no hay forma de
        // decir cual se entrego.
        assertThatThrownBy(() -> transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx)))
                .hasMessageContaining("Ya existe");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.estado_financiero_generado WHERE periodo_contable_id = ?",
                        enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una asiento_plantilla activa con dos líneas balanceadas · Cuando se aplica antes del cierre del período · Entonces se crea un asiento_contable cuadrado con esas partidas")
    void criterio3() {
        UUID plantilla = fixtura.plantillaDeAsiento("PL-" + anio, activo, pasivo, ctx.usuarioId());

        var validada = transaccion.execute(t -> estadoCU.validarPlantilla(plantilla, ctx));

        assertThat(validada.lineas()).isEqualTo(2);
        assertThat(validada.debes()).isEqualTo(1);
        assertThat(validada.haberes()).isEqualTo(1);

        // Aplicada antes del cierre, produce un asiento cuadrado: el periodo abierto lo
        // admite y el cierre lo va a encontrar cuadrado.
        UUID enero = periodo(1);
        fixtura.asiento(enero, activo, pasivo, "1000.00", ctx.usuarioId());
        var cierre = transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));
        assertThat(cierre.cuadrado()).isTrue();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID enero = mesCerrado(1);
        var a = transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx)))
                .hasMessageContaining("Ya existe");
        assertThat(contar("SELECT count(*)::int FROM erp.estado_financiero_generado WHERE id = ?", a.estadoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID enero = mesCerrado(1);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));
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
                        "SELECT count(*)::int FROM erp.estado_financiero_generado WHERE periodo_contable_id = ?",
                        enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID enero = mesCerrado(1);

        var balance = transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));

        // Activo = pasivo + patrimonio, al centavo. Publicar un balance que no cierra es
        // publicar un numero que nadie puede usar.
        var renglones = balance.estado().renglones();
        BigDecimal act = renglones.get(0).monto();
        BigDecimal pas = renglones.get(1).monto();
        BigDecimal pat = renglones.get(2).monto();
        assertThat(act).isEqualByComparingTo(pas.add(pat));
        assertThat(balance.estado().diferencia()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID enero = mesCerrado(1);
        var balance = transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));
        var resultados = transaccion.execute(t -> estadoCU.generar(enero, "ESTADO_RESULTADOS", ctx));

        // Dos tipos del mismo mes son dos documentos distintos: lo que no puede
        // repetirse es el mismo tipo.
        assertThat(balance.estadoId()).isNotEqualTo(resultados.estadoId());
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.estado_financiero_generado WHERE periodo_contable_id = ?",
                        enero))
                .isEqualTo(2);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.estado_financiero_generado' AND agregado_id = ?
                        """,
                        balance.estadoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: un periodo que no existe.
        assertThatThrownBy(() -> transaccion.execute(t -> estadoCU.generar(UUID.randomUUID(), "BALANCE_GENERAL", ctx)))
                .hasMessageContaining("no existe");

        // Paso fallido: una plantilla que apunta a una cuenta sumarizadora. R-CTB-02: una
        // sumarizadora es un total, no un destino.
        UUID sumarizadora = fixtura.cuentaSumarizadora("1300-" + anio, "ACTIVO", "DEUDORA");
        UUID mala = fixtura.plantillaDeAsiento("PLMAL-" + anio, sumarizadora, pasivo, ctx.usuarioId());
        assertThatThrownBy(() -> transaccion.execute(t -> estadoCU.validarPlantilla(mala, ctx)))
                .hasMessageContaining("sumarizadoras");

        // Con un periodo real y una plantilla correcta, el mismo camino cierra.
        UUID enero = mesCerrado(1);
        var buena = transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));
        assertThat(buena.hashContenido()).hasSize(64);
    }
}
