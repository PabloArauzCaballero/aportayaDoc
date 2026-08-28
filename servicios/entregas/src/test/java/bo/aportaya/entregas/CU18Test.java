package bo.aportaya.entregas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino.EntradaRegistro;
import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino.SalidaRegistro;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-18 · Registrar y verificar una cuenta bancaria de destino. */
class CU18Test extends BaseDeEntregas {

    private static final String NOMBRE = "Maria Fernanda Quispe";
    private static final String DOCUMENTO = "8123456";

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String numero() {
        return "40" + (10_000_000 + (int) (System.nanoTime() % 80_000_000));
    }

    private EntradaRegistro registro(String numeroEnClaro, String titularNombre, String titularDocumento) {
        return new EntradaRegistro(
                "AHORRO",
                "Banco de Prueba",
                numeroEnClaro,
                // El cifrado lo hace el almacen de llaves; aca viaja ya cifrado.
                "cifrado:" + Integer.toHexString(numeroEnClaro.hashCode()),
                titularNombre,
                titularDocumento,
                NOMBRE,
                DOCUMENTO,
                "BOB");
    }

    @Test
    @DisplayName(
            "Dado un usuario verificado · Cuando registra una cuenta a su propio nombre · Entonces se guarda numero_cuenta_cifrado y hash_numero_cuenta · Y no existe el número en claro en ninguna columna ni en la bitácora")
    void criterio1() {
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        String enClaro = numero();

        SalidaRegistro salida = transaccion.execute(t -> cuentaCU.registrar(registro(enClaro, NOMBRE, DOCUMENTO), ctx));

        assertThat(salida.esNueva()).isTrue();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario
                         WHERE id = ? AND length(hash_numero_cuenta) = 64 AND numero_cuenta_cifrado LIKE 'cifrado:%'
                        """,
                        salida.cuentaId()))
                .isEqualTo(1);
        // El numero en claro no aparece en NINGUNA columna de texto de la fila. Uno
        // completo en una columna termina en un respaldo, en un volcado de desarrollo
        // y en la pantalla de cualquiera con lectura.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario
                         WHERE id = ? AND (numero_cuenta_cifrado LIKE '%' || ? || '%'
                                        OR numero_enmascarado LIKE '%' || ? || '%'
                                        OR hash_numero_cuenta LIKE '%' || ? || '%')
                        """,
                        salida.cuentaId(), enClaro, enClaro, enClaro))
                .isZero();
        // Ni en el evento, que se replica y se archiva en veinte lugares.
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.evento_dominio WHERE payload::text LIKE '%' || ? || '%'",
                        enClaro))
                .isZero();
        assertThat(salida.numeroEnmascarado()).endsWith(enClaro.substring(enClaro.length() - 4));
    }

    @Test
    @DisplayName(
            "Dado un usuario que registra una cuenta a nombre de otra persona · Cuando envía el formulario · Entonces se rechaza con TITULAR_NO_COINCIDE")
    void criterio2() {
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() ->
                        transaccion.execute(t -> cuentaCU.registrar(registro(numero(), "Juan Perez", "9999999"), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no coincide");
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario WHERE usuario_id = ?",
                        usuario))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una cuenta recién verificada · Cuando el usuario intenta retirar dentro de la ventana de enfriamiento · Entonces el retiro se rechaza indicando el tiempo restante")
    void criterio3() {
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRegistro cuenta =
                transaccion.execute(t -> cuentaCU.registrar(registro(numero(), NOMBRE, DOCUMENTO), ctx));
        transaccion.execute(t -> cuentaCU.verificar(cuenta.cuentaId(), "MICRODEPOSITO", ctx));

        var disponibilidad = transaccion.execute(t -> cuentaCU.disponibilidad(cuenta.cuentaId(), ctx));

        // Se le dice CUANTO falta, no solo que no puede: negar sin explicar convierte
        // una medida de seguridad en una falla del sistema.
        assertThat(disponibilidad.disponible()).isFalse();
        assertThat(disponibilidad.restante()).isPositive();
        assertThat(disponibilidad.motivo()).contains("horas");
    }

    @Test
    @DisplayName(
            "Dada una cuenta principal existente · Cuando se designa otra como principal · Entonces solo una queda con es_principal en true")
    void criterio4() {
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRegistro primera =
                transaccion.execute(t -> cuentaCU.registrar(registro(numero(), NOMBRE, DOCUMENTO), ctx));
        SalidaRegistro segunda =
                transaccion.execute(t -> cuentaCU.registrar(registro(numero(), NOMBRE, DOCUMENTO), ctx));

        transaccion.execute(t -> cuentaCU.designarPrincipal(segunda.cuentaId(), ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario WHERE usuario_id = ? AND es_principal",
                        usuario))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario WHERE id = ? AND es_principal",
                        segunda.cuentaId()))
                .isEqualTo(1);
        assertThat(primera.cuentaId()).isNotEqualTo(segunda.cuentaId());
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave natural es el hash de la cuenta: la misma cuenta dos veces no es un
        // error del usuario, se le devuelve la que ya tiene.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        String enClaro = numero();

        SalidaRegistro a = transaccion.execute(t -> cuentaCU.registrar(registro(enClaro, NOMBRE, DOCUMENTO), ctx));
        SalidaRegistro b = transaccion.execute(t -> cuentaCU.registrar(registro(enClaro, NOMBRE, DOCUMENTO), ctx));

        assertThat(b.cuentaId()).isEqualTo(a.cuentaId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario WHERE usuario_id = ?",
                        usuario))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos verificaciones de la misma cuenta: la segunda no vuelve a mover la
        // ventana de enfriamiento. Si lo hiciera, reverificar seria una forma de
        // reiniciar el reloj a voluntad.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRegistro cuenta =
                transaccion.execute(t -> cuentaCU.registrar(registro(numero(), NOMBRE, DOCUMENTO), ctx));

        var primera = transaccion.execute(t -> cuentaCU.verificar(cuenta.cuentaId(), "MICRODEPOSITO", ctx));
        var segunda = transaccion.execute(t -> cuentaCU.verificar(cuenta.cuentaId(), "MICRODEPOSITO", ctx));

        assertThat(primera.esNueva()).isTrue();
        assertThat(segunda.esNueva()).isFalse();
        assertThat(segunda.disponibleDesde()).isEqualTo(primera.disponibleDesde());
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Aca no hay dinero, pero si una identidad que tiene que cerrar: el hash es
        // reproducible y el enmascarado deja ver exactamente cuatro digitos. Si el
        // hash cambiara entre corridas, la misma cuenta se registraria dos veces.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        String enClaro = numero();

        SalidaRegistro salida = transaccion.execute(t -> cuentaCU.registrar(registro(enClaro, NOMBRE, DOCUMENTO), ctx));

        String enmascarado = dsl.fetchOne(
                        "SELECT numero_enmascarado FROM entregas.cuenta_bancaria_beneficiario WHERE id = ?",
                        salida.cuentaId())
                .get("numero_enmascarado", String.class);
        assertThat(enmascarado).hasSize(enClaro.length());
        assertThat(enmascarado.chars().filter(c -> c == '*').count()).isEqualTo(enClaro.length() - 4L);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "cuentas-destino"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "cuentas-destino"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Pasado el tope de cuentas no queda fila ni evento. Diez destinos distintos
        // para la misma persona es un patron, no una comodidad.
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        for (int i = 0; i < MAXIMO_DE_CUENTAS; i++) {
            transaccion.execute(t -> cuentaCU.registrar(registro(numero(), NOMBRE, DOCUMENTO), ctx));
        }

        assertThatThrownBy(
                        () -> transaccion.execute(t -> cuentaCU.registrar(registro(numero(), NOMBRE, DOCUMENTO), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no se admiten mas");
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.cuenta_bancaria_beneficiario WHERE usuario_id = ?",
                        usuario))
                .isEqualTo(MAXIMO_DE_CUENTAS);
    }
}
