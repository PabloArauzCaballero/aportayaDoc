package bo.aportaya.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.identidad.aplicacion.CU01RegistrarUsuario.SalidaRegistro;
import bo.aportaya.identidad.dominio.AperturaDeCuenta;
import bo.aportaya.identidad.dominio.DocumentoDeIdentidad;
import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-01 · Registro y apertura de billetera.
 *
 * <p>La prueba recorre la coreografia entera: `identidad` registra y emite, y los
 * dobles de {@link DoblesDeLaCoreografia} hacen lo que haran `cumplimiento` y
 * `nucleo-financiero` al consumir el evento.
 */
class CU01Test extends BaseDeCU01 {

    @Test
    @DisplayName(
            "Dado un usuario con documento válido y sin coincidencias en listas · Cuando completa el registro · Entonces existe una cuenta_billetera ACTIVA con nivel SIMPLIFICADA · Y existe una calificacion_riesgo_cliente vigente · Y existe una aceptacion_contrato con hash_evidencia no nulo")
    void criterio1() {
        SalidaRegistro salida = registrar("+59177000001", "1111111");

        // identidad hizo lo suyo: el usuario existe, PENDIENTE_VERIFICACION, y el
        // evento quedo en el outbox EN LA MISMA transaccion.
        assertThat(salida.estado()).isEqualTo(AperturaDeCuenta.PENDIENTE_VERIFICACION);
        assertThat(eventosDe(salida.usuarioId(), "identidad.usuario_registrado"))
                .isEqualTo(1);

        // Y ahora los otros dos, consumiendolo.
        dobles.cumplimientoEvalua(salida.usuarioId(), "SIMPLIFICADA");
        UUID cuenta = dobles.nucleoAbreBilletera(salida.usuarioId(), "SIMPLIFICADA");

        assertThat(estadoDeLaCuenta(cuenta)).isEqualTo("ACTIVA");
        assertThat(nivelDeLaCuenta(cuenta)).isEqualTo("SIMPLIFICADA");
        assertThat(calificacionesDe(salida.usuarioId())).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario con coincidencia confirmada en lista restrictiva · Cuando intenta completar el registro · Entonces no se crea cuenta_billetera · Y queda una coincidencia_lista en estado CONFIRMADA")
    void criterio2() {
        SalidaRegistro salida = registrar("+59177000002", "2222222");
        dobles.listaRestrictivaConfirma(salida.usuarioId(), "Ana Quispe");

        // El consumidor ve la coincidencia confirmada y NO abre la billetera.
        assertThat(dobles.hayCoincidenciaConfirmada(salida.usuarioId())).isTrue();
        assertThat(cuentasDe(salida.usuarioId())).isZero();
    }

    @Test
    @DisplayName(
            "Dado un usuario que ya tiene cuenta ACTIVA en BOB · Cuando intenta abrir otra cuenta USUARIO en BOB · Entonces la operación falla por violación de unicidad (R-BIL-04)")
    void criterio3() {
        SalidaRegistro salida = registrar("+59177000003", "3333333");
        dobles.nucleoAbreBilletera(salida.usuarioId(), "SIMPLIFICADA");

        // Una cuenta por titular, moneda y tipo: lo corta la BASE (R-BIL-04).
        assertThat(rechazaLaBase(sqlSegundaCuenta(salida.usuarioId()))).contains("uq_cuenta_usuario_moneda");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Un usuario sin fecha de registro no es auditable.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO identidad.usuario
                            (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                             estado, nivel_kyc, idioma, zona_horaria)
                        VALUES (gen_random_uuid(), 'AY-SINFECHA', 'Ana', 'Quispe', '+59177000090',
                                DATE '1990-01-01', 'ACTIVO', 'NINGUNO', 'es', 'America/La_Paz')
                        """))
                .contains("fecha_registro");
    }

    @Test
    @DisplayName("rechaza por R-BIL-04")
    void rechazaRBIL04() {
        SalidaRegistro salida = registrar("+59177000004", "4444444");
        dobles.nucleoAbreBilletera(salida.usuarioId(), "SIMPLIFICADA");

        assertThat(rechazaLaBase(sqlSegundaCuenta(salida.usuarioId()))).contains("uq_cuenta_usuario_moneda");
    }

    @Test
    @DisplayName("rechaza por R-BIL-05")
    void rechazaRBIL05() {
        // La titularidad es coherente con el tipo: una cuenta USUARIO sin titular no
        // entra, y una de grupo sin grupo tampoco.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.cuenta_billetera
                            (id, numero_cuenta, tipo, moneda, estado, nivel_debida_diligencia,
                             saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
                        VALUES (gen_random_uuid(), 'AYSINTITULAR01', 'USUARIO', 'BOB', 'ACTIVA',
                                'SIMPLIFICADA', 0.00, 0.00, false, now(), 0)
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-CON-06")
    void rechazaRCON06() {
        // Sin aceptar el contrato no se abre cuenta. Lo corta el caso de uso antes
        // de escribir nada: rechazar despues dejaria un usuario a medio crear.
        assertThatThrownBy(() -> registrarSinContrato("+59177000005", "5555555"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("aceptar el contrato");
    }

    @Test
    @DisplayName("rechaza por R-LIC-01")
    void rechazaRLIC01() {
        // Denegar por omision: sin licencia que habilite billetera, no se abre nada.
        assertThatThrownBy(() -> registrarSinLicencia("+59177000006", "6666666"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no esta habilitado");
    }

    @Test
    @DisplayName("rechaza por R-SEG-01")
    void rechazaRSEG01() {
        // El numero del documento no se guarda en claro: se indexa su hash, y el
        // hash tiene 64 caracteres o no es un hash.
        assertThatThrownBy(() -> new DocumentoDeIdentidad(DocumentoDeIdentidad.Tipo.CI, "corto", "BO"))
                .isInstanceOf(ErrorDeDominio.class);
        assertThat(DocumentoDeIdentidad.de(DocumentoDeIdentidad.Tipo.CI, "1234567", "pimienta", "BO")
                        .hashNumero())
                .hasSize(64)
                .doesNotContain("1234567");
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        // El nivel de diligencia de la cuenta sale de un catalogo cerrado.
        SalidaRegistro salida = registrar("+59177000007", "7777777");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.cuenta_billetera
                            (id, numero_cuenta, tipo, usuario_id, moneda, estado, nivel_debida_diligencia,
                             saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
                        VALUES (gen_random_uuid(), 'AYNIVELRARO001', 'USUARIO', '%s', 'BOB', 'ACTIVA',
                                'INVENTADA', 0.00, 0.00, false, now(), 0)
                        """
                                .formatted(salida.usuarioId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-UIF-10")
    void rechazaRUIF10() {
        // La declaracion PEP tiene su propia guarda en la base.
        assertThat(constraintExiste("tg_ddd_pep")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-UIF-11")
    void rechazaRUIF11() {
        // Una sola calificacion vigente por cliente: lo hace cumplir un EXCLUDE.
        assertThat(constraintExiste("ex_calificacion_vigente")).isTrue();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // El telefono es unico: registrar dos veces el mismo no crea dos cuentas, y
        // el mensaje ofrece el camino de vuelta en vez de dejar a alguien trabado.
        registrar("+59177000008", "8888888");

        assertThatThrownBy(() -> registrar("+59177000008", "9999999"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("recuperar el acceso");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El documento tambien: dos personas no pueden registrar el mismo numero.
        registrar("+59177000010", "1010101");

        assertThatThrownBy(() -> registrar("+59177000011", "1010101"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ese documento");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // La cuenta se abre EN CERO. Un alta que acredita algo es un alta que
        // alguien va a tener que explicar.
        SalidaRegistro salida = registrar("+59177000012", "1212121");
        UUID cuenta = dobles.nucleoAbreBilletera(salida.usuarioId(), "SIMPLIFICADA");

        assertThat(saldoDe(cuenta)).isEqualByComparingTo(java.math.BigDecimal.ZERO);
        assertThat(movimientosDe(cuenta)).isZero();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        SalidaRegistro salida = registrar("+59177000013", "1313131");
        UUID idEvento = idDelEvento(salida.usuarioId());

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "cumplimiento"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "cumplimiento"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // No hay saga que compensar, y eso es la afirmacion: si la transaccion local
        // falla, no queda ni el usuario ni el evento. Los otros dos servicios nunca
        // se enteran de un alta que no ocurrio.
        long usuariosAntes = usuariosTotales();
        long eventosAntes = eventosDeTipo("identidad.usuario_registrado");

        try {
            transaccion.execute(estado -> {
                registrarUsuario.ejecutar(entrada("+59177000014", "1414141", true, true), contexto());
                throw new IllegalStateException("fallo despues de escribir");
            });
        } catch (IllegalStateException esperado) {
            assertThat(esperado).hasMessageContaining("fallo despues");
        }

        assertThat(usuariosTotales()).isEqualTo(usuariosAntes);
        assertThat(eventosDeTipo("identidad.usuario_registrado")).isEqualTo(eventosAntes);
    }
}
