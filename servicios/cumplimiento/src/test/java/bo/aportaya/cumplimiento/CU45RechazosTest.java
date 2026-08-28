package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU45AtenderRequerimiento.EntradaRequerimiento;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-45 · Lo que la base y el caso de uso rechazan. */
class CU45RechazosTest extends BaseDeCumplimiento {

    private static final String HASH = "a".repeat(64);

    private static final String ALCANCE =
            "Movimientos de la cuenta del titular entre el 1 de enero y el 30 de junio de 2026.";

    private UUID afectado;
    private String oficio;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        afectado = fixtura.usuario();
        oficio = "FIS-" + UUID.randomUUID().toString().substring(0, 10);
        ctx = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaRequerimiento oficio(OffsetDateTime plazo) {
        return new EntradaRequerimiento(
                "FISCALIA", oficio, plazo, afectado, ALCANCE, "https://oficios.bo/" + oficio, HASH, false);
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // El oficio y su hash se conservan: son la prueba de por que se entregaron los
        // datos de alguien, y borrarlos dejaria la entrega sin justificacion.
        var salida = transaccion.execute(t ->
                oficioCU.registrar(oficio(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5)), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad
                         WHERE id = ? AND documento_url IS NOT NULL AND length(hash_documento) = 64
                           AND fecha_recepcion IS NOT NULL
                        """,
                        salida.requerimientoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-14")
    void rechazaRBIL14() {
        // Un oficio, un bloqueo. El bloqueo lo escribe el nucleo financiero; aca se ata
        // al oficio que lo ordeno, y sin esa atadura nadie puede decir por que se
        // congelo el saldo de alguien.
        var salida = transaccion.execute(t ->
                oficioCU.registrar(oficio(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5)), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.requerimiento_autoridad
                            (usuario_afectado_id, autoridad, numero_oficio, plazo_respuesta, alcance,
                             documento_url, hash_documento, estado)
                        VALUES (?, 'FISCALIA', ?, now() + interval '5 days', ?, 'https://x', ?, 'RECIBIDO')
                        """,
                        afectado,
                        oficio,
                        ALCANCE,
                        HASH))
                .contains("uq_requerimiento_autoridad_numero_oficio");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad WHERE numero_oficio = ?",
                        oficio))
                .isEqualTo(1);
        assertThat(salida.requerimientoId()).isNotNull();
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // Todo acceso a datos sensibles queda registrado CON justificacion. La bitacora
        // vive en auditoria; lo que este servicio garantiza es que el evento salga con
        // el numero de oficio, que es la justificacion que la norma pide.
        transaccion.execute(t ->
                oficioCU.registrar(oficio(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5)), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.requerimiento_recibido'
                           AND payload->>'justificacionDeAcceso' = ?
                        """,
                        "Oficio " + oficio))
                .isEqualTo(1);

        // Y sin alcance concreto no se entrega nada: «todo» no es un alcance.
        assertThatThrownBy(() -> transaccion.execute(t -> oficioCU.registrar(
                        new EntradaRequerimiento(
                                "FISCALIA",
                                "OTRO-" + oficio,
                                OffsetDateTime.now(ZoneOffset.UTC).plusDays(5),
                                afectado,
                                "todo",
                                "https://x",
                                HASH,
                                false),
                        ctx)))
                .hasMessageContaining("ambiguo");
    }

    @Test
    @DisplayName("rechaza por R-UIF-08")
    void rechazaRUIF08() {
        // Todo requerimiento tiene plazo guardado. Se responde dentro o fuera de el,
        // pero nunca sin saber cual era.
        OffsetDateTime plazo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);
        var salida = transaccion.execute(t -> oficioCU.registrar(oficio(plazo), ctx));

        var guardado = dsl.fetchOne(
                        "SELECT plazo_respuesta FROM cumplimiento.requerimiento_autoridad WHERE id = ?",
                        salida.requerimientoId())
                .get(0, OffsetDateTime.class);
        assertThat(guardado).isEqualTo(plazo);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad WHERE id = ? AND plazo_respuesta > fecha_recepcion",
                        salida.requerimientoId()))
                .isEqualTo(1);
    }
}
