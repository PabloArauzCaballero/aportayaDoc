package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU48CalibrarReglas.EntradaActivacion;
import bo.aportaya.cumplimiento.aplicacion.CU48CalibrarReglas.EntradaRegla;
import bo.aportaya.cumplimiento.aplicacion.CU48CalibrarReglas.EntradaTriaje;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-48 · Lo que la base y el caso de uso rechazan. */
class CU48RechazosTest extends BaseDeCumplimiento {

    private static final String FUNDAMENTO =
            "Las operaciones responden al giro comercial declarado y verificado del titular.";

    private String codigo;
    private ContextoSesion oficial;

    @BeforeEach
    void escenario() {
        codigo = "RM-" + UUID.randomUUID().toString().substring(0, 10);
        oficial = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaRegla regla(String expresion, String severidad, String accion) {
        return new EntradaRegla(
                codigo,
                "FRACCIONAMIENTO",
                "Operaciones sucesivas bajo el umbral",
                expresion,
                "{\"tipo\":\"fraccionamiento\"}",
                "PCC01_CARGA",
                "DIA",
                null,
                null,
                severidad,
                accion,
                "Instructivo EIF art. 54");
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La alerta cerrada conserva su conclusion: es lo que despues explica por que
        // el analista decidio que no era nada.
        UUID reglaId = gobiernoFixtura.reglaDeMonitoreo(codigo, "MEDIA", true);
        UUID alertaId = gobiernoFixtura.alerta(
                reglaId, fixtura.usuario(), "MEDIA", "3000.00", OffsetDateTime.now(ZoneOffset.UTC));
        transaccion.execute(t -> {
            reglaCU.triar(new EntradaTriaje(alertaId, "SIN_MERITO", FUNDAMENTO), oficial);
            return null;
        });

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.alerta_monitoreo_lft
                         WHERE id = ? AND conclusion IS NOT NULL AND cerrada_en IS NOT NULL
                        """,
                        alertaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        UUID reglaId = transaccion.execute(
                t -> reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "ALTA", "SOLO_ALERTAR"), oficial));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.regla_monitoreo_creada' AND agregado_id = ?
                           AND payload->>'activa' = 'false'
                        """,
                        reglaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // Una regla vigente sin firma no la puso nadie: activar exige quien aprueba.
        UUID reglaId = transaccion.execute(
                t -> reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "ALTA", "SOLO_ALERTAR"), oficial));

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft WHERE id = ? AND activa = false AND aprobada_por IS NULL",
                        reglaId))
                .isEqualTo(1);

        transaccion.execute(t -> {
            reglaCU.activar(new EntradaActivacion(reglaId, oficial.usuarioId(), true, false, "0.0120"), oficial);
            return null;
        });
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft WHERE id = ? AND activa = true AND aprobada_por IS NOT NULL",
                        reglaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        // Denegar por omision: sin simulacion no se activa nada. Encender una regla a
        // ciegas es apostar con la bandeja de otro.
        UUID reglaId = transaccion.execute(
                t -> reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "ALTA", "SOLO_ALERTAR"), oficial));

        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reglaCU.activar(new EntradaActivacion(reglaId, oficial.usuarioId(), false, false, "0"), oficial);
                    return null;
                }))
                .hasMessageContaining("simulacion previa");
    }

    @Test
    @DisplayName("rechaza por R-UIF-01")
    void rechazaRUIF01() {
        // El umbral apunta al catalogo. Un numero dentro de la expresion obliga a
        // desplegar para cumplir una circular, y la circular no espera al despliegue.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> reglaCU.crear(regla("monto_usd >= 10000", "ALTA", "SOLO_ALERTAR"), oficial)))
                .hasMessageContaining("va al catalogo");

        // Y una regla que declara un umbral y no lo usa esta mal escrita: dice apuntar
        // a un catalogo que despues ignora.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> reglaCU.crear(regla("cantidad > umbral('OTRO_CODIGO')", "ALTA", "SOLO_ALERTAR"), oficial)))
                .hasMessageContaining("no lo usa");
    }

    @Test
    @DisplayName("rechaza por R-UIF-02")
    void rechazaRUIF02() {
        // La regla nace INACTIVA. Una que entra encendida puede marcar el 40% del
        // trafico y nadie lo sabe hasta que la bandeja explota.
        UUID reglaId = transaccion.execute(
                t -> reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "ALTA", "SOLO_ALERTAR"), oficial));

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft WHERE id = ? AND activa = false",
                        reglaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-UIF-07")
    void rechazaRUIF07() {
        // Una alerta no se cierra sin fundamento, ni con uno de tres palabras.
        UUID reglaId = gobiernoFixtura.reglaDeMonitoreo(codigo, "MEDIA", true);
        UUID alertaId = gobiernoFixtura.alerta(
                reglaId, fixtura.usuario(), "MEDIA", "3000.00", OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reglaCU.triar(new EntradaTriaje(alertaId, "SIN_MERITO", "no es nada"), oficial);
                    return null;
                }))
                .hasMessageContaining("sin fundamento");
        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.alerta_monitoreo_lft SET estado = 'DESCARTADA' WHERE id = ?", alertaId))
                .contains("ck_alerta_conclusion");
    }

    @Test
    @DisplayName("rechaza por R-UIF-08")
    void rechazaRUIF08() {
        // La accion automatica no excede lo que la severidad habilita: entre no hacer
        // nada y bloquearle la cuenta a alguien hay dos escalones.
        assertThatThrownBy(() -> transaccion.execute(t ->
                        reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "BAJA", "BLOQUEAR_CUENTA"), oficial)))
                .hasMessageContaining("excede lo que la severidad");
        assertThatThrownBy(() -> transaccion.execute(t -> reglaCU.crear(
                        regla("monto_usd >= umbral('PCC01_CARGA')", "MEDIA", "RETENER_OPERACION"), oficial)))
                .hasMessageContaining("excede lo que la severidad");
    }
}
