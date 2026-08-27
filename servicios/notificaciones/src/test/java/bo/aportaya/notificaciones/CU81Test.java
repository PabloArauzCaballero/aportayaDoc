package bo.aportaya.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.notificaciones.aplicacion.CU81ProgramarRecordatorios.EntradaRecordatorios;
import bo.aportaya.notificaciones.aplicacion.CU81ProgramarRecordatorios.Obligacion;
import bo.aportaya.notificaciones.aplicacion.CU81ProgramarRecordatorios.SalidaRecordatorios;
import bo.aportaya.notificaciones.dominio.EscaleraDeRecordatorios;
import bo.aportaya.notificaciones.dominio.EscaleraDeRecordatorios.Escalon;
import bo.aportaya.notificaciones.dominio.EscaleraDeRecordatorios.Resultado;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-81 · Programar recordatorios de aporte. */
class CU81Test extends BaseDeNotificaciones {

    private static final int TOPE_DIARIO = 2;

    /**
     * El «hoy» del caso de uso, no el de la maquina.
     *
     * <p>El trabajo calcula la fecha en UTC —como todo el codigo, via {@code Reloj} y
     * {@code ZoneOffset.UTC}— y la maquina que corre la prueba esta en -04:00. Usar
     * {@code LocalDate.now()} hace que la prueba pase u falle segun la hora del dia,
     * que es la peor clase de prueba: verde a la manana, roja a la noche.
     */
    private static LocalDate hoyEnUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
    }

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    /** La escalera de la plataforma: previo, vencimiento y gracia. */
    private UUID escaleraDePlataforma() {
        UUID evento = fixtura.evento("aporte.recordatorio", "COBRANZA", false, "NORMAL");
        for (int desfase : new int[] {-3, 0, 2}) {
            dslFixtura.execute(
                    """
                    INSERT INTO notificaciones.programacion_recordatorio
                        (id, grupo_id, evento_id, desfase_dias, hora_envio, max_repeticiones, condicion, activa)
                    VALUES (gen_random_uuid(), NULL, ?, ?, TIME '09:00', 1, 'SIEMPRE', true)
                    """,
                    evento,
                    (short) desfase);
        }
        return evento;
    }

    private List<Obligacion> obligaciones(int cuantas, boolean pagadas, int enviosPrevios) {
        List<Obligacion> lista = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            lista.add(new Obligacion(UUID.randomUUID(), fixtura.usuario(), pagadas, false, enviosPrevios));
        }
        return lista;
    }

    private SalidaRecordatorios correr(LocalDate vencimiento, List<Obligacion> obligaciones, ContextoSesion ctx) {
        return transaccion.execute(e -> recordatoriosCU.ejecutar(
                new EntradaRecordatorios(
                        UUID.randomUUID(), Optional.empty(), vencimiento, true, TOPE_DIARIO, obligaciones),
                ctx));
    }

    @Test
    @DisplayName(
            "Dado un período recién abierto con seis obligaciones pendientes · Cuando corre la programación · Entonces existen recordatorios para los escalones previo, vencimiento y gracia")
    void criterio1() {
        escaleraDePlataforma();
        ContextoSesion ctx = contexto();
        // Vencimiento hoy: el escalon VENCIMIENTO cae hoy y las seis se avisan.
        SalidaRecordatorios salida = correr(hoyEnUtc(), obligaciones(6, false, 0), ctx);

        assertThat(salida.programados()).isEqualTo(6);
        assertThat(salida.enviados()).isEqualTo(6);
        // Y la escalera tiene sus tres escalones, con las fechas que corresponden.
        var pasos = EscaleraDeRecordatorios.calcular(
                LocalDate.of(2026, 8, 26), Map.of(Escalon.PREVIO, -3, Escalon.VENCIMIENTO, 0, Escalon.GRACIA, 2));
        assertThat(pasos).hasSize(3);
        assertThat(pasos.get(0).fecha()).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(pasos.get(2).fecha()).isEqualTo(LocalDate.of(2026, 8, 28));
    }

    @Test
    @DisplayName(
            "Dada una obligación pagada antes del recordatorio · Cuando corre el trabajo diario · Entonces el recordatorio se cancela con motivo YA_PAGADO y no se envía")
    void criterio2() {
        escaleraDePlataforma();
        ContextoSesion ctx = contexto();

        SalidaRecordatorios salida = correr(hoyEnUtc(), obligaciones(3, true, 0), ctx);

        assertThat(salida.cancelados()).isEqualTo(3);
        assertThat(salida.enviados()).isZero();
        assertThat(salida.detalle()).allMatch(d -> d.resultado().equals("CANCELADO_YA_PAGADO"));
        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.evento_dominio WHERE tipo = ?",
                        "notificaciones.recordatorio_debido"))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un usuario que ya alcanzó el tope diario · Cuando le corresponde otro recordatorio · Entonces queda pospuesto para el día siguiente")
    void criterio3() {
        escaleraDePlataforma();
        ContextoSesion ctx = contexto();

        SalidaRecordatorios salida = correr(hoyEnUtc(), obligaciones(2, false, TOPE_DIARIO), ctx);

        assertThat(salida.pospuestos()).isEqualTo(2);
        assertThat(salida.enviados()).isZero();
    }

    @Test
    @DisplayName(
            "Dado el mismo trabajo diario ejecutado dos veces · Cuando corre la segunda vez · Entonces no se duplica ningún envío")
    void criterio4() {
        escaleraDePlataforma();
        ContextoSesion ctx = contexto();
        List<Obligacion> pendientes = obligaciones(2, false, 0);

        correr(hoyEnUtc(), pendientes, ctx);
        // La segunda corrida ve los envios previos: el tope las frena.
        List<Obligacion> conEnvio = pendientes.stream()
                .map(o -> new Obligacion(o.obligacionId(), o.usuarioId(), false, false, TOPE_DIARIO))
                .toList();
        SalidaRecordatorios segunda = correr(hoyEnUtc(), conEnvio, ctx);

        assertThat(segunda.enviados()).isZero();
        assertThat(segunda.pospuestos()).isEqualTo(2);
    }

    @Test
    @DisplayName("rechaza por R-NOT-01")
    void rechazaRNOT01() {
        // El indice unico de idempotencia del envio vive en la base y es el que
        // impide que el mismo aviso salga dos veces, no un `if` del trabajo.
        assertThat(contar(
                        "SELECT count(*)::int FROM pg_indexes WHERE schemaname = 'notificaciones' AND indexname = ?",
                        "uq_envio_idempotencia"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-NOT-02")
    void rechazaRNOT02() {
        UUID usuario = fixtura.usuario();

        assertThat(rechazaLaBase("SELECT fn_not_puede_enviar('%s', true)".formatted(usuario)))
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-03")
    void rechazaRGRP03() {
        // uq_obligacion_periodo_cupo: una obligacion periodica por cupo, no dos.
        assertThat(contar("SELECT count(*)::int FROM pg_indexes WHERE indexname = ?", "uq_obligacion_periodo_cupo"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La idempotencia del trabajo sale del dia y del estado de la obligacion, no
        // de una clave: dos corridas con la misma entrada dan la misma salida.
        escaleraDePlataforma();
        ContextoSesion ctx = contexto();
        List<Obligacion> pagadas = obligaciones(2, true, 0);

        SalidaRecordatorios primera = correr(hoyEnUtc(), pagadas, ctx);
        SalidaRecordatorios segunda = correr(hoyEnUtc(), pagadas, ctx);

        assertThat(segunda.cancelados()).isEqualTo(primera.cancelados());
        assertThat(segunda.enviados()).isEqualTo(primera.enviados());
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos corridas del mismo dia sobre la MISMA obligacion no mandan dos avisos:
        // la segunda ya ve el envio previo y el tope la frena.
        escaleraDePlataforma();
        ContextoSesion ctx = contexto();
        UUID obligacion = UUID.randomUUID();
        UUID usuario = fixtura.usuario();

        correr(hoyEnUtc(), List.of(new Obligacion(obligacion, usuario, false, false, 0)), ctx);
        SalidaRecordatorios segunda =
                correr(LocalDate.now(), List.of(new Obligacion(obligacion, usuario, false, false, TOPE_DIARIO)), ctx);

        assertThat(segunda.enviados()).isZero();
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-81 no mueve dinero: lo que cuadra es que cada obligacion cae en UNA sola
        // categoria, y que el orden de las preguntas sea el correcto.
        assertThat(EscaleraDeRecordatorios.debeRecordar(true, true, 99, 1, true))
                .isEqualTo(Resultado.CANCELADO_YA_PAGADO);
        assertThat(EscaleraDeRecordatorios.debeRecordar(false, true, 99, 1, true))
                .isEqualTo(Resultado.SUPRIMIDO);
        assertThat(EscaleraDeRecordatorios.debeRecordar(false, false, 99, 1, true))
                .isEqualTo(Resultado.POSPUESTO_TOPE);
        assertThat(EscaleraDeRecordatorios.debeRecordar(false, false, 0, 1, true))
                .isEqualTo(Resultado.ENVIADO);
        // Y el total siempre cierra: enviados + cancelados + pospuestos = programados.
        escaleraDePlataforma();
        ContextoSesion ctx = contexto();
        List<Obligacion> mezcla = new ArrayList<>(obligaciones(2, true, 0));
        mezcla.addAll(obligaciones(3, false, 0));
        SalidaRecordatorios salida = correr(hoyEnUtc(), mezcla, ctx);

        assertThat(salida.enviados() + salida.cancelados() + salida.pospuestos())
                .isEqualTo(salida.programados());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "aportes"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "aportes"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Un periodo cerrado corta ANTES de emitir nada: no puede quedar un
        // recordatorio pedido para un periodo que ya se liquido.
        escaleraDePlataforma();
        ContextoSesion ctx = contexto();

        assertThatThrownBy(() -> transaccion.execute(e -> recordatoriosCU.ejecutar(
                        new EntradaRecordatorios(
                                UUID.randomUUID(),
                                Optional.empty(),
                                hoyEnUtc(),
                                false,
                                TOPE_DIARIO,
                                obligaciones(3, false, 0)),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no esta abierto");

        assertThat(contar(
                        "SELECT count(*)::int FROM notificaciones.evento_dominio WHERE tipo = ?",
                        "notificaciones.recordatorio_debido"))
                .isZero();
    }

    @Test
    @DisplayName("rechaza inventar una escalera: sin programacion propia se aplican solo los obligatorios")
    void rechazaEscaleraInventada() {
        // AP-CU81-02. Un grupo sin escalera hereda las filas de plataforma; no se
        // fabrica una por omision, que seria inventar politica de cobranza.
        ContextoSesion ctx = contexto();
        fixtura.evento("aporte.recordatorio", "COBRANZA", false, "NORMAL");

        SalidaRecordatorios salida = correr(hoyEnUtc(), obligaciones(2, false, 0), ctx);

        assertThat(salida.conEscaleraPropia()).isFalse();
        assertThat(salida.enviados()).isZero();
        assertThat(salida.pospuestos()).isEqualTo(2);
    }
}
