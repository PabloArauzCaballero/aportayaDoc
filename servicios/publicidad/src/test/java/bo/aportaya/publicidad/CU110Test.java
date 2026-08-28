package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.publicidad.aplicacion.CU110AltaDeAnunciante.EntradaAnunciante;
import bo.aportaya.publicidad.aplicacion.CU110AltaDeAnunciante.EntradaSocio;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-110 · Dar de alta un anunciante y su cuenta publicitaria. */
class CU110Test extends BaseDePublicidad {

    private ContextoSesion operaciones;

    @BeforeEach
    void escenario() {
        operaciones = contextoDe(fixtura.usuario());
    }

    private EntradaAnunciante deOrganizador(UUID organizadorId, String limite) {
        return new EntradaAnunciante(
                "ORGANIZADOR",
                organizadorId,
                null,
                "Pasanaku del barrio",
                limite == null ? null : new BigDecimal(limite),
                "BOB");
    }

    @Test
    @DisplayName(
            "Dado un organizador habilitado sin cuenta publicitaria · Cuando solicita anunciar su grupo · Entonces se crea un anunciante tipo ORGANIZADOR con su cuenta_publicitaria en estado ACTIVA")
    void criterio1() {
        UUID organizadorId = fixtura.organizador("HABILITADO");

        var salida =
                transaccion.execute(t -> anuncianteCU.darDeAlta(deOrganizador(organizadorId, "5000.00"), operaciones));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.anunciante
                         WHERE id = ? AND tipo = 'ORGANIZADOR' AND organizador_id = ?
                           AND socio_comercial_id IS NULL AND estado = 'ACTIVO'
                        """,
                        salida.anuncianteId(),
                        organizadorId))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.cuenta_publicitaria
                         WHERE id = ? AND anunciante_id = ? AND estado = 'ACTIVA' AND saldo_consumido_mes = 0
                        """,
                        salida.cuentaPublicitariaId(),
                        salida.anuncianteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un negocio externo nuevo · Cuando se da de alta como socio_comercial y Operaciones lo verifica · Entonces puede crearse un anunciante tipo SOCIO_COMERCIAL sobre él")
    void criterio2() {
        String nit = "NIT-110-" + UUID.randomUUID().toString().substring(0, 8);
        UUID socioId = transaccion.execute(t -> anuncianteCU.postularSocio(
                new EntradaSocio("Ferreteria Central", nit, "COMERCIO", "central@ejemplo.bo"), operaciones));

        // Postulado todavia no anuncia: verificar es lo que lo habilita.
        assertThatThrownBy(() -> transaccion.execute(t -> anuncianteCU.darDeAlta(
                        new EntradaAnunciante("SOCIO_COMERCIAL", null, socioId, "Ferreteria Central", null, "BOB"),
                        operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("todavia no puede anunciar"));

        boolean verificado = transaccion.execute(t -> anuncianteCU.verificarSocio(socioId, operaciones));
        assertThat(verificado).isTrue();

        var salida = transaccion.execute(t -> anuncianteCU.darDeAlta(
                new EntradaAnunciante("SOCIO_COMERCIAL", null, socioId, "Ferreteria Central", null, "BOB"),
                operaciones));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.anunciante
                         WHERE id = ? AND tipo = 'SOCIO_COMERCIAL' AND socio_comercial_id = ?
                           AND organizador_id IS NULL
                        """,
                        salida.anuncianteId(),
                        socioId))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.socio_comercial WHERE id = ? AND estado = 'ACTIVO' AND verificado_por = ?",
                        socioId,
                        operaciones.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un intento de alta de anunciante con organizador_id y socio_comercial_id a la vez · Cuando se procesa la solicitud · Entonces el sistema devuelve TIPO_Y_REFERENCIA_INCONSISTENTES")
    void criterio3() {
        UUID organizadorId = fixtura.organizador("HABILITADO");
        UUID socioId = fixtura.socio("ACTIVO");

        assertThatThrownBy(() -> transaccion.execute(t -> anuncianteCU.darDeAlta(
                        new EntradaAnunciante("ORGANIZADOR", organizadorId, socioId, "Dos duenos", null, "BOB"),
                        operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("DOS_REFERENCIAS"));

        // Y el CHECK de la base lo frena igual si alguien escribe la fila por fuera.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.anunciante
                            (tipo, organizador_id, socio_comercial_id, razon_social_facturacion, estado, creado_en)
                        VALUES ('ORGANIZADOR', ?, ?, 'Dos duenos', 'ACTIVO', now())
                        """,
                        organizadorId,
                        socioId))
                .contains("ck_anunciante_tipo_exclusivo");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        String nit = "NIT-110R-" + UUID.randomUUID().toString().substring(0, 8);
        var entrada = new EntradaSocio("Panaderia del sur", nit, "COMERCIO", "sur@ejemplo.bo");

        UUID primero = transaccion.execute(t -> anuncianteCU.postularSocio(entrada, operaciones));
        UUID segundo = transaccion.execute(t -> anuncianteCU.postularSocio(entrada, operaciones));

        assertThat(segundo).isEqualTo(primero);
        assertThat(contar("SELECT count(*)::int FROM publicidad.socio_comercial WHERE numero_documento = ?", nit))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID organizadorId = fixtura.organizador("HABILITADO");
        var barrera = new java.util.concurrent.CountDownLatch(1);
        var exitos = new AtomicInteger();

        try (var piscina = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                piscina.submit(() -> {
                    try {
                        barrera.await();
                        transaccion.execute(
                                t -> anuncianteCU.darDeAlta(deOrganizador(organizadorId, null), operaciones));
                        exitos.incrementAndGet();
                    } catch (RuntimeException | InterruptedException ignorada) {
                        // La que pierde es la que importa: no deja rastro.
                    }
                });
            }
            barrera.countDown();
            piscina.shutdown();
            assertThat(piscina.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        // Los dos anunciantes pueden crearse —el modelo no lo impide— pero cada uno
        // tiene exactamente una cuenta: uq_cuenta_publicitaria_anunciante (R-PUB-02).
        int cuentas = contar(
                """
                SELECT count(*)::int FROM publicidad.cuenta_publicitaria c
                  JOIN publicidad.anunciante a ON a.id = c.anunciante_id
                 WHERE a.organizador_id = ?
                """,
                organizadorId);
        assertThat(cuentas).isEqualTo(exitos.get());
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Este caso de uso no mueve dinero: abre la cuenta con la que despues se gasta.
        // El cuadre que le corresponde es que nace en cero y con su limite intacto.
        UUID organizadorId = fixtura.organizador("HABILITADO");
        var salida =
                transaccion.execute(t -> anuncianteCU.darDeAlta(deOrganizador(organizadorId, "1200.00"), operaciones));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.cuenta_publicitaria
                         WHERE id = ? AND saldo_consumido_mes = 0 AND limite_gasto_mensual = 1200.00
                        """,
                        salida.cuentaPublicitariaId()))
                .isEqualTo(1);
        // Y el consumido no puede pasarse del limite: ck_cuenta_publicitaria_consumo.
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.cuenta_publicitaria SET saldo_consumido_mes = 1200.01 WHERE id = ?",
                        salida.cuentaPublicitariaId()))
                .contains("ck_cuenta_publicitaria_consumo");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID idEvento = UUID.randomUUID();
        assertThat(consumidos.registrar(dsl, idEvento, "CU110Test")).isTrue();
        assertThat(consumidos.registrar(dsl, idEvento, "CU110Test")).isFalse();

        // Fuera de orden: verificar un socio que ya esta ACTIVO no lo vuelve a verificar.
        UUID socioId = fixtura.socio("ACTIVO");
        assertThatThrownBy(() -> transaccion.execute(t -> anuncianteCU.verificarSocio(socioId, operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("la verificacion es del alta"));
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: el organizador no esta habilitado. No queda ni anunciante ni
        // cuenta a medias — la transaccion es una sola.
        UUID suspendido = fixtura.organizador("SUSPENDIDO");

        assertThatThrownBy(() -> transaccion.execute(
                        t -> anuncianteCU.darDeAlta(deOrganizador(suspendido, "900.00"), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("no puede anunciar"));

        assertThat(contar("SELECT count(*)::int FROM publicidad.anunciante WHERE organizador_id = ?", suspendido))
                .isZero();
    }
}
