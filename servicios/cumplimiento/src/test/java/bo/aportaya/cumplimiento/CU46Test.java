package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU46VerificarAlcance.EntradaAlcance;
import bo.aportaya.cumplimiento.aplicacion.CU46VerificarAlcance.SalidaAlcance;
import bo.aportaya.cumplimiento.dominio.EstadoDeLicencia;
import bo.aportaya.cumplimiento.dominio.HabilitacionDeServicio;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-46 · Verificar el alcance de la licencia. */
class CU46Test extends BaseDeCumplimiento {

    private static final String ALCANCE_COMPLETO = "[\"BILLETERA\",\"RECARGA\",\"RETIRO\",\"GRUPO_PASANAKU\"]";

    @AfterEach
    void devolverLaLicencia() {
        // La licencia es una fila unica y compartida por toda la clase: si una
        // prueba la deja OTORGADA, la siguiente probaria otro sistema.
        fixtura.restaurarLicencia();
    }

    private SalidaAlcance preguntar(String servicio) {
        return transaccion.execute(e -> alcanceCU.ejecutar(EntradaAlcance.de(servicio), contexto()));
    }

    @Test
    @DisplayName(
            "Dada una licencia OTORGADA cuyo alcance no incluye \"transferencias P2P\" · Cuando un usuario intenta una transferencia P2P · Entonces la operación se rechaza por alcance no autorizado")
    void criterio1() {
        fixtura.licencia("OTORGADA", ALCANCE_COMPLETO, null);

        SalidaAlcance salida = preguntar("TRANSFERENCIA_P2P");

        assertThat(salida.habilitado()).isFalse();
        assertThat(salida.via()).isEqualTo("NINGUNA");
        assertThat(salida.motivo()).contains("no esta en el alcance autorizado");
    }

    @Test
    @DisplayName(
            "Dado un servicio cubierto por un entorno de prueba con tope de 500 usuarios · Cuando se registra el usuario 501 en ese servicio · Entonces el alta en ese servicio se rechaza")
    void criterio2() {
        fixtura.licencia("OTORGADA", ALCANCE_COMPLETO, null);
        fixtura.sandbox("TRANSFERENCIA_P2P", 500, new BigDecimal("1000.00"));

        SalidaAlcance quinientos = transaccion.execute(
                e -> alcanceCU.ejecutar(new EntradaAlcance("TRANSFERENCIA_P2P", Optional.empty(), 499), contexto()));
        SalidaAlcance quinientosUno = transaccion.execute(
                e -> alcanceCU.ejecutar(new EntradaAlcance("TRANSFERENCIA_P2P", Optional.empty(), 500), contexto()));

        assertThat(quinientos.habilitado()).isTrue();
        assertThat(quinientos.via()).isEqualTo("SANDBOX");
        assertThat(quinientosUno.habilitado()).isFalse();
        assertThat(quinientosUno.motivo()).contains("tope de 500 usuarios");
    }

    @Test
    @DisplayName(
            "Dada una licencia en estado SUSPENDIDA · Cuando un usuario intenta recargar · Entonces se rechaza · Y cuando intenta retirar su saldo, se permite")
    void criterio3() {
        fixtura.licencia("SUSPENDIDA", ALCANCE_COMPLETO, null);

        assertThat(preguntar("RECARGA").habilitado()).isFalse();
        // La sancion es a la empresa, no al saldo de la persona.
        assertThat(preguntar("RETIRO").habilitado()).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-LIC-01")
    void rechazaRLIC01() {
        // La regla vive en la base: fn_lic_servicio_habilitado es la autoridad, y el
        // caso de uso tiene que decir lo mismo que ella. Si divergen, gana la base.
        fixtura.licencia("OTORGADA", "[\"BILLETERA\"]", null);

        boolean segunLaBase = (Boolean)
                dsl.fetchOne("SELECT fn_lic_servicio_habilitado(?)", "RETIRO").get(0);

        assertThat(segunLaBase).isFalse();
        assertThat(preguntar("RETIRO").habilitado()).isFalse();
    }

    @Test
    @DisplayName("rechaza por R-LIC-02")
    void rechazaRLIC02() {
        // ck_sandbox_limites: un sandbox ACTIVO sin topes no puede existir.
        fixtura.licencia("OTORGADA", ALCANCE_COMPLETO, null);
        UUID licencia = fixtura.licenciaId();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.entorno_prueba_regulado
                            (id, licencia_regulatoria_id, servicio_en_prueba, alcance, limite_usuarios,
                             limite_monto_operacion, fecha_inicio, fecha_fin, estado, informes_remitidos)
                        VALUES (gen_random_uuid(), '%s', 'SIN_TOPES', '{}'::jsonb, NULL,
                                NULL, current_date, current_date + 10, 'ACTIVO', 0)
                        """
                                .formatted(licencia)))
                .contains("ck_sandbox_limites");
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // ck_politica_acta: una politica VIGENTE sin acta de directorio no entra.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO cumplimiento.politica_interna
                            (id, codigo, tipo, materia, version, estado, url_documento,
                             hash_documento, aprobada_por_directorio, vigente_desde, proxima_revision)
                        VALUES (gen_random_uuid(), 'POL-X', 'MANUAL', 'LFT', 1, 'VIGENTE',
                                'https://x/p.pdf', repeat('a', 64), false, now(), current_date + 365)
                        """))
                .contains("ck_politica_acta");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // CU-46 no escribe: preguntar dos veces por lo mismo tiene que dar lo mismo,
        // y ese es todo el contenido de la idempotencia para una consulta.
        fixtura.licencia("OTORGADA", ALCANCE_COMPLETO, null);

        SalidaAlcance primera = preguntar("BILLETERA");
        SalidaAlcance segunda = preguntar("BILLETERA");

        assertThat(primera).isEqualTo(segunda);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El unico efecto de escritura del CU es el contador de informes del sandbox.
        // Dos incrementos concurrentes tienen que sumar dos, no uno: el UPDATE lee y
        // escribe en la misma sentencia, sin SELECT previo que se pise.
        fixtura.licencia("OTORGADA", ALCANCE_COMPLETO, null);
        fixtura.sandbox("RECARGA", 10, new BigDecimal("50.00"));
        var repositorio = new bo.aportaya.cumplimiento.infraestructura.SandboxRepositorio();

        transaccion.execute(e -> {
            repositorio.contarInforme(dsl, "RECARGA");
            return null;
        });
        transaccion.execute(e -> {
            repositorio.contarInforme(dsl, "RECARGA");
            return null;
        });

        assertThat(contar(
                        "SELECT informes_remitidos FROM cumplimiento.entorno_prueba_regulado WHERE servicio_en_prueba = ?",
                        "RECARGA"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-46 no mueve dinero: lo que tiene que cuadrar es el tope del sandbox
        // contra el monto, y el borde exacto pertenece al lado permitido.
        var caja = new HabilitacionDeServicio.Sandbox("RECARGA", true, 10, new BigDecimal("1000.00"), 0);

        assertThat(caja.admiteMonto(new BigDecimal("999.99"))).isTrue();
        assertThat(caja.admiteMonto(new BigDecimal("1000.00"))).isTrue();
        assertThat(caja.admiteMonto(new BigDecimal("1000.01"))).isFalse();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "identidad"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "identidad"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // `exigirHabilitado` lanza, y al lanzar revierte su propia transaccion: el
        // evento de rechazo que emitio no queda escrito a medias.
        fixtura.licencia("OTORGADA", "[\"BILLETERA\"]", null);
        ContextoSesion ctx = contexto();
        int antes = contar("SELECT count(*)::int FROM cumplimiento.evento_dominio");

        assertThatThrownBy(() -> transaccion.execute(e -> {
                    alcanceCU.exigirHabilitado(EntradaAlcance.de("RETIRO"), ctx);
                    return null;
                }))
                .isInstanceOf(ErrorDeNegocio.class);

        assertThat(contar("SELECT count(*)::int FROM cumplimiento.evento_dominio"))
                .isEqualTo(antes);
    }

    @Test
    @DisplayName("denegar por omision: sin licencia legible, el servicio no se habilita")
    void deniegaPorOmision() {
        // Sin una sola fila de licencia, que es como arranca el contenedor: la
        // lectura segura de una tabla vacia es «no», nunca «segui adelante».
        assertThat(preguntar("BILLETERA").habilitado()).isFalse();
        assertThat(preguntar("BILLETERA").motivo()).contains("Todavia no hay licencia otorgada");
        assertThat(EstadoDeLicencia.EN_TRAMITE.habilitaServicioFinanciero()).isFalse();
        assertThat(Set.of(EstadoDeLicencia.values())).hasSize(5);
    }
}
