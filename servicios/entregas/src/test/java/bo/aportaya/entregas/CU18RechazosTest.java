package bo.aportaya.entregas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino.EntradaRegistro;
import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino.SalidaRegistro;
import bo.aportaya.entregas.dominio.CuentaEnmascarada;
import bo.aportaya.entregas.dominio.TitularidadDeCuenta;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-18 · las pruebas de RECHAZO, una por restriccion citada. */
class CU18RechazosTest extends BaseDeEntregas {

    private static final String NOMBRE = "Maria Fernanda Quispe";
    private static final String DOCUMENTO = "8123456";

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String numero() {
        return "40" + (10_000_000 + (int) (System.nanoTime() % 80_000_000));
    }

    private EntradaRegistro registro(String enClaro) {
        return new EntradaRegistro(
                "AHORRO", "Banco de Prueba", enClaro, "cifrado:x", NOMBRE, DOCUMENTO, NOMBRE, DOCUMENTO, "BOB");
    }

    private SalidaRegistro registrar(ContextoSesion ctx) {
        return transaccion.execute(t -> cuentaCU.registrar(registro(numero()), ctx));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La cuenta verificada conserva su fecha y su metodo: sin ellos no hay forma de
        // saber cuando ni como se comprobo la titularidad.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRegistro cuenta = registrar(ctx);
        transaccion.execute(t -> cuentaCU.verificar(cuenta.cuentaId(), "MICRODEPOSITO", ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario
                         WHERE id = ? AND verificada_en IS NOT NULL AND metodo_verificacion IS NOT NULL
                        """,
                        cuenta.cuentaId()))
                .isEqualTo(1);
        assertThat(rechazaLaBase(
                        "UPDATE entregas.cuenta_bancaria_beneficiario SET hash_numero_cuenta = NULL WHERE id = '%s'"
                                .formatted(cuenta.cuentaId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRegistro cuenta = registrar(ctx);

        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "entregas.cuenta_destino_registrada",
                        cuenta.cuentaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-09")
    void rechazaRBIL09() {
        // El retiro exige instrumento verificado del titular. Una cuenta PENDIENTE no
        // esta disponible, y una recien verificada tampoco: la ventana de enfriamiento
        // le da al titular real el tiempo de enterarse si alguien tomo su sesion.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRegistro cuenta = registrar(ctx);

        var sinVerificar = transaccion.execute(t -> cuentaCU.disponibilidad(cuenta.cuentaId(), ctx));
        assertThat(sinVerificar.disponible()).isFalse();
        assertThat(sinVerificar.motivo()).contains("todavia no esta verificada");

        transaccion.execute(t -> cuentaCU.verificar(cuenta.cuentaId(), "MICRODEPOSITO", ctx));
        var enfriando = transaccion.execute(t -> cuentaCU.disponibilidad(cuenta.cuentaId(), ctx));
        assertThat(enfriando.disponible()).isFalse();
        assertThat(enfriando.restante()).isPositive();
    }

    @Test
    @DisplayName("rechaza por R-BIL-17")
    void rechazaRBIL17() {
        // Una cuenta de destino por titular y numero, y una sola principal. La BASE
        // sostiene las dos: dos principales harian que el destino de un retiro
        // dependiera del orden en que la base devuelve las filas.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRegistro cuenta = registrar(ctx);
        String hash = dsl.fetchOne(
                        "SELECT hash_numero_cuenta FROM entregas.cuenta_bancaria_beneficiario WHERE id = ?",
                        cuenta.cuentaId())
                .get("hash_numero_cuenta", String.class);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.cuenta_bancaria_beneficiario
                            (id, usuario_id, tipo_cuenta, entidad_financiera, numero_cuenta_cifrado,
                             version_llave, hash_numero_cuenta, numero_enmascarado, titular_nombre,
                             titular_documento, moneda, es_principal, estado_verificacion)
                        VALUES (gen_random_uuid(), '%s', 'AHORRO', 'Otro banco', 'cifrado:y', 1, '%s',
                                '****1111', 'Maria', '8123456', 'BOB', false, 'PENDIENTE')
                        """
                                .formatted(usuario, hash)))
                .contains("uq_cuenta_benef_hash");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.cuenta_bancaria_beneficiario
                            (id, usuario_id, tipo_cuenta, entidad_financiera, numero_cuenta_cifrado,
                             version_llave, hash_numero_cuenta, numero_enmascarado, titular_nombre,
                             titular_documento, moneda, es_principal, estado_verificacion)
                        VALUES (gen_random_uuid(), '%s', 'AHORRO', 'Otro banco', 'cifrado:z', 1,
                                repeat('f', 64), '****2222', 'Maria', '8123456', 'BOB', true, 'PENDIENTE')
                        """
                                .formatted(usuario)))
                .contains("uq_cuenta_benef_principal");
    }

    @Test
    @DisplayName("rechaza por R-SEG-01")
    void rechazaRSEG01() {
        // Nunca se persiste el numero completo, y el hash de busqueda lleva pimienta.
        // Sin pimienta, un numero de cuenta se descubre probando: el espacio de
        // numeros posibles es chico.
        assertThatThrownBy(() -> CuentaEnmascarada.de("4012345678", null, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pimienta");
        assertThatThrownBy(() -> CuentaEnmascarada.de("4012345678", "   ", 1))
                .isInstanceOf(IllegalStateException.class);

        // Y con pimientas distintas el mismo numero da hashes distintos: eso es lo que
        // impide comparar contra una tabla precalculada.
        assertThat(CuentaEnmascarada.de("4012345678", "una", 1).hash())
                .isNotEqualTo(CuentaEnmascarada.de("4012345678", "otra", 1).hash());
        assertThat(CuentaEnmascarada.de("4012345678", PIMIENTA, 1).enmascarado())
                .isEqualTo("******5678");
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // La cuenta es de quien la registra. Se compara por DOCUMENTO, que es unico:
        // dos personas se pueden llamar igual, y una sola letra de diferencia no
        // deberia habilitar un desvio.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(t -> cuentaCU.registrar(
                        new EntradaRegistro(
                                "AHORRO",
                                "Banco",
                                numero(),
                                "cifrado:x",
                                "Juan Perez",
                                "9999999",
                                NOMBRE,
                                DOCUMENTO,
                                "BOB"),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no coincide");

        // El mismo documento con el nombre de otro tampoco pasa: ataja el caso del
        // documento mal tipeado que por azar coincide.
        assertThat(TitularidadDeCuenta.coincide("Juan Perez", DOCUMENTO, NOMBRE, DOCUMENTO))
                .isFalse();
        // Y tolera acentos, mayusculas y espacios de mas — no tolera otra persona.
        assertThat(TitularidadDeCuenta.coincide("  maría  fernanda   QUISPE ", DOCUMENTO, NOMBRE, DOCUMENTO))
                .isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario WHERE usuario_id = ?",
                        usuario))
                .isZero();
    }
}
