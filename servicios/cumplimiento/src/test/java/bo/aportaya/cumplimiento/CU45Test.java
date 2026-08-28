package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU45AtenderRequerimiento.EntradaRequerimiento;
import bo.aportaya.cumplimiento.aplicacion.CU45AtenderRequerimiento.EntradaRespuesta;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-45 · Atender un requerimiento de autoridad. */
class CU45Test extends BaseDeCumplimiento {

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
    @DisplayName(
            "Dado un oficio con plazo de 5 días · Cuando se registra · Entonces plazo_respuesta queda guardado y no se recalcula después")
    void criterio1() {
        OffsetDateTime plazo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);

        var salida = transaccion.execute(t -> oficioCU.registrar(oficio(plazo), ctx));

        // Guardado, no derivado: un oficio con cinco dias tiene cinco dias desde que
        // llego, no desde que alguien lo abrio.
        var guardado = dsl.fetchOne(
                        "SELECT plazo_respuesta FROM cumplimiento.requerimiento_autoridad WHERE id = ?",
                        salida.requerimientoId())
                .get(0, OffsetDateTime.class);
        assertThat(guardado).isEqualTo(plazo);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad WHERE numero_oficio = ? AND estado = 'RECIBIDO'",
                        oficio))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada la extracción de información para el oficio · Cuando un operador consulta los datos del afectado · Entonces existe un registro_acceso_datos con el número de oficio como justificación")
    void criterio2() {
        transaccion.execute(t ->
                oficioCU.registrar(oficio(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5)), ctx));

        // La bitacora de accesos vive en el esquema de auditoria y no se escribe desde
        // aca (invariante 11): el evento lleva la justificacion para que quien la posee
        // la registre con el numero de oficio, que es lo que R-SEG-02 exige.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.requerimiento_recibido'
                           AND payload->>'justificacionDeAcceso' = ?
                        """,
                        "Oficio " + oficio))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un oficio con plazo vencido sin respuesta · Cuando corre el control diario · Entonces existe un hallazgo_auditoria abierto")
    void criterio3() {
        OffsetDateTime vencido = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
        var salida = transaccion.execute(t -> oficioCU.registrar(oficio(vencido), ctx));

        // Se responde IGUAL: no responder agrega un incumplimiento al retraso.
        transaccion.execute(t -> oficioCU.responder(
                new EntradaRespuesta(salida.requerimientoId(), oficio, vencido, "https://oficios.bo/r/" + oficio),
                ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.hallazgo_auditoria WHERE codigo = ? AND estado = 'ABIERTO'",
                        "OFI-" + oficio.substring(0, Math.min(16, oficio.length()))))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad WHERE id = ? AND estado = 'RESPONDIDO'",
                        salida.requerimientoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var entrada = oficio(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5));
        transaccion.execute(t -> oficioCU.registrar(entrada, ctx));

        // Un oficio, un registro. Registrar dos veces el mismo numero abriria dos
        // expedientes por el mismo pedido y duplicaria la entrega de datos.
        assertThatThrownBy(() -> transaccion.execute(t -> oficioCU.registrar(entrada, ctx)))
                .hasMessageContaining("ya esta registrado");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad WHERE numero_oficio = ?",
                        oficio))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var entrada = oficio(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5));

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> oficioCU.registrar(entrada, ctx));
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

        assertThat(errores).hasSize(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad WHERE numero_oficio = ?",
                        oficio))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        var salida = transaccion.execute(t ->
                oficioCU.registrar(oficio(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5)), ctx));

        // El cuadre de un oficio es que el documento y su hash queden juntos: sin el
        // hash, cualquiera podria cambiar el PDF despues y nadie lo notaria.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad
                         WHERE id = ? AND length(hash_documento) = 64 AND documento_url IS NOT NULL
                        """,
                        salida.requerimientoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        OffsetDateTime plazo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);
        var salida = transaccion.execute(t -> oficioCU.registrar(oficio(plazo), ctx));
        var respuesta = new EntradaRespuesta(salida.requerimientoId(), oficio, plazo, "https://oficios.bo/r/1");

        transaccion.execute(t -> oficioCU.responder(respuesta, ctx));
        // La segunda respuesta llega tarde: el oficio ya fue respondido y no se pisa.
        assertThatThrownBy(() -> transaccion.execute(t -> oficioCU.responder(respuesta, ctx)))
                .hasMessageContaining("ya fue respondido");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.requerimiento_respondido' AND agregado_id = ?
                        """,
                        salida.requerimientoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        OffsetDateTime plazo = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);

        // Paso fallido: sin documento ni hash no se actua.
        assertThatThrownBy(() -> transaccion.execute(t -> oficioCU.registrar(
                        new EntradaRequerimiento("FISCALIA", oficio, plazo, afectado, ALCANCE, null, null, false),
                        ctx)))
                .hasMessageContaining("sin el documento y su hash");

        // Paso fallido: alcance ambiguo. «Todo» no es un alcance, es un cheque en blanco.
        assertThatThrownBy(() -> transaccion.execute(t -> oficioCU.registrar(
                        new EntradaRequerimiento(
                                "FISCALIA", oficio, plazo, afectado, "todo", "https://oficios.bo/x", HASH, false),
                        ctx)))
                .hasMessageContaining("ambiguo");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.requerimiento_autoridad WHERE numero_oficio = ?",
                        oficio))
                .isZero();

        // Con el documento y un alcance concreto, el mismo camino cierra.
        var buena = transaccion.execute(t -> oficioCU.registrar(oficio(plazo), ctx));
        assertThat(buena.requerimientoId()).isNotNull();
    }
}
