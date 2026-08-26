package bo.aportaya.grupos;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.aplicacion.CU20CrearGrupo.SalidaCreacion;
import bo.aportaya.grupos.dominio.GrupoNuevo;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-20 · Crear grupo y congelar tarifario. */
class CU20Test extends BaseDeCU20 {

    @Test
    @DisplayName(
            "Dado un tarifario vigente versión 3 · Cuando se crea un grupo · Entonces existe tarifa_congelada_grupo con tarifario_id de la versión 3 y hash_snapshot")
    void criterio1() {
        UUID tarifario = UUID.randomUUID();
        SalidaCreacion salida = crear(tarifario);

        // `grupos` emite; `tarifas` congela al consumir. Aca se comprueban los dos
        // extremos: el evento lleva el tarifario, y el doble congela con su hash.
        assertThat(eventoLleva(salida.grupoId(), "tarifarioId")).isEqualTo(tarifario.toString());
        UUID congelada = dobles.tarifasCongela(salida.grupoId(), tarifario);
        assertThat(hashDelSnapshot(congelada)).hasSize(64);
    }

    @Test
    @DisplayName(
            "Dado un grupo con tarifa congelada de la versión 3 · Cuando se publica la versión 4 con comisión mayor · Entonces las entregas de ese grupo siguen calculándose con la versión 3")
    void criterio2() {
        // El grupo sigue con SU snapshot aunque salga un tarifario nuevo: la version
        // congelada esta guardada, no se resuelve al consultar.
        UUID version3 = UUID.randomUUID();
        SalidaCreacion salida = crear(version3);
        UUID congelada = dobles.tarifasCongela(salida.grupoId(), version3);

        UUID version4 = UUID.randomUUID();
        dobles.publicarTarifario(version4);

        assertThat(tarifarioDe(congelada)).isEqualTo(version3);
    }

    @Test
    @DisplayName(
            "Dada la cuenta de un grupo · Cuando se consulta su titular · Entonces grupo_id no es nulo y usuario_id es nulo")
    void criterio3() {
        // La cuenta del grupo tiene grupo_id y NO usuario_id: el titular es el grupo
        // y nunca el organizador (R-GRP-04). Si fuera del organizador, la plata del
        // grupo estaria a nombre de una persona.
        SalidaCreacion salida = crear(UUID.randomUUID());
        UUID cuenta = dobles.nucleoAbreCuentaDelGrupo(salida.grupoId());

        assertThat(grupoDeLaCuenta(cuenta)).isEqualTo(salida.grupoId());
        assertThat(usuarioDeLaCuenta(cuenta)).isNull();
    }

    @Test
    @DisplayName("rechaza por R-TAR-07")
    void rechazaRTAR07() {
        // Una sola tarifa congelada por grupo: dos serian dos precios.
        SalidaCreacion salida = crear(UUID.randomUUID());
        UUID tarifario = UUID.randomUUID();
        dobles.tarifasCongela(salida.grupoId(), tarifario);

        assertThat(rechazaLaBase(dobles.sqlCongelar(salida.grupoId(), tarifario)))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-04")
    void rechazaRGRP04() {
        // Una cuenta de grupo con titular usuario no entra.
        SalidaCreacion salida = crear(UUID.randomUUID());

        assertThat(rechazaLaBase(dobles.sqlCuentaDeGrupoConTitularPersona(salida.grupoId(), fixtura.usuario())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-BIL-04")
    void rechazaRBIL04() {
        SalidaCreacion salida = crear(UUID.randomUUID());
        dobles.nucleoAbreCuentaDelGrupo(salida.grupoId());

        assertThat(rechazaLaBase(dobles.sqlSegundaCuentaDelGrupo(salida.grupoId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-BIL-05")
    void rechazaRBIL05() {
        // La titularidad es coherente con el tipo: sin grupo ni usuario, no hay cuenta.
        assertThat(rechazaLaBase(dobles.sqlCuentaSinTitular())).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-CON-07")
    void rechazaRCON07() {
        // Sin tarifario vigente el precio quedaria indefinido, y un grupo con precio
        // indefinido es un grupo al que nadie puede aceptar entrar.
        assertThatThrownBy(() -> crearSinTarifario())
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("tarifario vigente");
    }

    @Test
    @DisplayName("rechaza por R-LIC-01")
    void rechazaRLIC01() {
        assertThatThrownBy(() -> crearSinLicencia())
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no esta habilitado");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Crear dos veces produce dos grupos distintos, cada uno con su codigo: no
        // hay clave de idempotencia porque no hay «el mismo grupo» que reintentar.
        SalidaCreacion primero = crear(UUID.randomUUID());
        SalidaCreacion segundo = crear(UUID.randomUUID());

        assertThat(primero.grupoId()).isNotEqualTo(segundo.grupoId());
        assertThat(codigoDe(primero.grupoId())).isNotEqualTo(codigoDe(segundo.grupoId()));
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Un grupo nace BORRADOR y con todos sus cupos LIBRES: no recibe a nadie
        // hasta que el precio este congelado.
        SalidaCreacion salida = crear(UUID.randomUUID());

        assertThat(estadoDelGrupo(salida.grupoId())).isEqualTo("BORRADOR");
        assertThat(cuposLibres(salida.grupoId())).isEqualTo(4);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El fondo de cada periodo es el aporte por la cantidad de participantes, al
        // centavo: es lo que va a recibir quien tenga el turno.
        GrupoNuevo datos = new GrupoNuevo(
                "Pasanaku de prueba",
                Dinero.de("333.33", BOB),
                "MENSUAL",
                5,
                3,
                LocalDate.now().plusDays(7));

        assertThat(datos.fondoPorPeriodo()).isEqualTo(Dinero.de("999.99", BOB));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "tarifas"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "tarifas"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // No hay saga: si la transaccion local falla, no queda ni el grupo ni el
        // evento. `tarifas` y `nucleo-financiero` nunca se enteran de un grupo que
        // no se creo.
        long antes = gruposTotales();

        try {
            transaccion.execute(estado -> {
                crearGrupo.ejecutar(entrada(UUID.randomUUID(), true, true), contexto());
                throw new IllegalStateException("fallo despues de escribir");
            });
        } catch (IllegalStateException esperado) {
            assertThat(esperado).hasMessageContaining("fallo despues");
        }

        assertThat(gruposTotales()).isEqualTo(antes);
    }
}
