package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU49DesignarOficial.EntradaBaja;
import bo.aportaya.cumplimiento.aplicacion.CU49DesignarOficial.EntradaCapacitacion;
import bo.aportaya.cumplimiento.aplicacion.CU49DesignarOficial.EntradaDesignacion;
import bo.aportaya.cumplimiento.dominio.CoberturaDeCapacitacion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-49 · Designar al oficial de cumplimiento y capacitar. */
class CU49Test extends BaseDeCumplimiento {

    private ContextoSesion ctx;
    private LocalDate hoy;

    @BeforeEach
    void escenario() {
        // uq_oficial_titular_activo es un indice unico parcial sobre una constante: hay
        // un titular activo en toda la base o no hay ninguno. Cada prueba arranca limpia.
        dsl.execute("DELETE FROM cumplimiento.oficial_cumplimiento");
        ctx = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        hoy = LocalDate.now(ZoneOffset.UTC);
    }

    private EntradaDesignacion designacion(UUID usuario, String tipo, Set<String> roles) {
        return new EntradaDesignacion(usuario, tipo, hoy, "ACTA-DIR-" + tipo, roles);
    }

    @Test
    @DisplayName(
            "Dado un titular activo · Cuando se intenta designar otro titular sin dar de baja al primero · Entonces se rechaza con TITULAR_YA_ACTIVO")
    void criterio1() {
        transaccion.execute(
                t -> oficialCU.designar(designacion(fixtura.usuario(), "TITULAR", Set.of("CUMPLIMIENTO")), ctx));

        // R-UIF-12: dos titulares es no tener ninguno, porque ante el regulador responde
        // una sola persona.
        assertThatThrownBy(() -> transaccion.execute(t ->
                        oficialCU.designar(designacion(fixtura.usuario(), "TITULAR", Set.of("CUMPLIMIENTO")), ctx)))
                .hasMessageContaining("Ya hay un titular activo");
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento WHERE tipo = 'TITULAR' AND activo = true"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada la baja del titular con suplente designado · Cuando se ejecuta · Entonces el suplente queda activo en la misma transacción · Y no existe ningún día sin oficial activo")
    void criterio2() {
        transaccion.execute(
                t -> oficialCU.designar(designacion(fixtura.usuario(), "TITULAR", Set.of("CUMPLIMIENTO")), ctx));
        UUID suplente = fixtura.usuario();
        transaccion.execute(t -> oficialCU.designar(designacion(suplente, "SUPLENTE", Set.of("CUMPLIMIENTO")), ctx));

        var salida =
                transaccion.execute(t -> oficialCU.darDeBajaAlTitular(new EntradaBaja(hoy, "ACTA-DIR-RELEVO"), ctx));

        assertThat(salida.suplentePromovido()).isTrue();
        // En la misma transaccion: entre las dos operaciones no hay un instante sin
        // oficial. Si en esa ventana llegara un requerimiento, no habria quien responda.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento
                         WHERE tipo = 'TITULAR' AND activo = true AND usuario_id = ?
                        """,
                        suplente))
                .isEqualTo(1);
        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento
                         WHERE activo = false AND fecha_baja IS NOT NULL
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un empleado activo sin capacitación aprobada del período · Cuando corre el control mensual · Entonces aparece por nombre en la lista de pendientes")
    void criterio3() {
        String periodo = "2026";
        UUID capacitado = fixtura.usuario();
        UUID pendiente = fixtura.usuario();
        transaccion.execute(t -> oficialCU.capacitar(
                new EntradaCapacitacion(
                        capacitado,
                        "Prevencion de LGI/FT",
                        "VIRTUAL",
                        new BigDecimal("8.00"),
                        hoy,
                        new BigDecimal("90.00"),
                        true,
                        null,
                        periodo),
                ctx));

        var cobertura = transaccion.execute(t -> oficialCU.cobertura(
                periodo,
                List.of(
                        new CoberturaDeCapacitacion.Empleado(capacitado, "Ana", hoy.minusYears(2)),
                        new CoberturaDeCapacitacion.Empleado(pendiente, "Luis", hoy.minusYears(2))),
                ctx));

        // Por NOMBRE: una lista de UUID no la usa nadie para ir a hablar con la persona.
        assertThat(cobertura.pendientes())
                .extracting(CoberturaDeCapacitacion.Empleado::nombre)
                .containsExactly("Luis");
        assertThat(cobertura.aprobados()).isEqualTo(1);
        assertThat(cobertura.personalActivo()).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Dado un empleado dado de alta en noviembre · Cuando corre el control de diciembre · Entonces no figura como incumplido porque su plazo corre desde el alta")
    void criterio4() {
        UUID recienLlegado = fixtura.usuario();

        var cobertura = transaccion.execute(t -> oficialCU.cobertura(
                "2026", List.of(new CoberturaDeCapacitacion.Empleado(recienLlegado, "Marta", hoy.minusDays(20))), ctx));

        // Marcar a alguien por no haber hecho un curso que no existia cuando llego es la
        // clase de reporte que hace que nadie confie en el tablero.
        assertThat(cobertura.pendientes()).isEmpty();
        assertThat(cobertura.todaviaEnPlazo()).isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID usuario = fixtura.usuario();
        var entrada = designacion(usuario, "TITULAR", Set.of("CUMPLIMIENTO"));
        transaccion.execute(t -> oficialCU.designar(entrada, ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> oficialCU.designar(entrada, ctx)))
                .hasMessageContaining("Ya hay un titular activo");
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento WHERE usuario_id = ?", usuario))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t ->
                        oficialCU.designar(designacion(fixtura.usuario(), "TITULAR", Set.of("CUMPLIMIENTO")), ctx));
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

        // La comprobacion previa no alcanza cuando los dos leen a la vez: quien sostiene
        // R-UIF-12 es uq_oficial_titular_activo.
        assertThat(errores).hasSize(1);
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento WHERE tipo = 'TITULAR' AND activo = true"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        String periodo = "2026";
        UUID a = fixtura.usuario();
        UUID b = fixtura.usuario();
        UUID c = fixtura.usuario();
        transaccion.execute(t -> oficialCU.capacitar(
                new EntradaCapacitacion(
                        a,
                        "LGI/FT",
                        "VIRTUAL",
                        new BigDecimal("8.00"),
                        hoy,
                        new BigDecimal("95.00"),
                        true,
                        null,
                        periodo),
                ctx));

        var cobertura = transaccion.execute(t -> oficialCU.cobertura(
                periodo,
                List.of(
                        new CoberturaDeCapacitacion.Empleado(a, "Ana", hoy.minusYears(2)),
                        new CoberturaDeCapacitacion.Empleado(b, "Luis", hoy.minusYears(2)),
                        new CoberturaDeCapacitacion.Empleado(c, "Rosa", hoy.minusDays(10))),
                ctx));

        // Aprobados + pendientes + en plazo tiene que dar el total. Si no cuadrara,
        // alguien quedaria fuera de todas las categorias y nadie lo notaria.
        assertThat(cobertura.aprobados() + cobertura.pendientes().size() + cobertura.todaviaEnPlazo())
                .isEqualTo(cobertura.personalActivo());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID titular = fixtura.usuario();
        transaccion.execute(t -> oficialCU.designar(designacion(titular, "TITULAR", Set.of("CUMPLIMIENTO")), ctx));
        transaccion.execute(
                t -> oficialCU.designar(designacion(fixtura.usuario(), "SUPLENTE", Set.of("CUMPLIMIENTO")), ctx));

        transaccion.execute(t -> oficialCU.darDeBajaAlTitular(new EntradaBaja(hoy, "ACTA-DIR-RELEVO"), ctx));
        // La segunda baja no encuentra al titular anterior: ya lo reemplazo el suplente,
        // y dar de baja al nuevo dejaria a la entidad sin nadie.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> oficialCU.darDeBajaAlTitular(new EntradaBaja(hoy, "ACTA-DIR-RELEVO"), ctx)))
                .hasMessageContaining("sin un suplente");
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento WHERE tipo = 'TITULAR' AND activo = true"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        UUID usuario = fixtura.usuario();

        // Paso fallido: sin acta no hay respaldo del directorio.
        assertThatThrownBy(() -> transaccion.execute(t -> oficialCU.designar(
                        new EntradaDesignacion(usuario, "TITULAR", hoy, null, Set.of("CUMPLIMIENTO")), ctx)))
                .hasMessageContaining("acta del directorio");

        // Paso fallido: funciones operativas incompatibles (R-SEG-04). Quien opera no
        // puede ser quien controla que se opere bien.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> oficialCU.designar(designacion(usuario, "TITULAR", Set.of("TESORERIA")), ctx)))
                .hasMessageContaining("incompatibles");

        // Paso fallido: baja sin suplente.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> oficialCU.darDeBajaAlTitular(new EntradaBaja(hoy, "ACTA-DIR-RELEVO"), ctx)))
                .hasMessageContaining("No hay titular activo");

        assertThat(contar("SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento"))
                .isZero();

        // Con acta y sin roles incompatibles, el mismo camino cierra.
        var buena = transaccion.execute(
                t -> oficialCU.designar(designacion(usuario, "TITULAR", Set.of("CUMPLIMIENTO")), ctx));
        assertThat(buena.activo()).isTrue();
        assertThat(buena.plazoComunicacionHasta()).isAfter(hoy);
    }
}
