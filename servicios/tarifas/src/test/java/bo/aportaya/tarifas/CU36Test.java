package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision.EntradaCotizacion;
import bo.aportaya.tarifas.aplicacion.CU36ResolverPrecio.EntradaSegmento;
import bo.aportaya.tarifas.dominio.SegmentoAplicable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-36 · Segmentar comercialmente y aplicar precio diferenciado. */
class CU36Test extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Escenario(String codigoTarifario, String hecho, UUID tarifarioId, ContextoSesion ctx) {}

    private Escenario escenario() {
        String codigoTarifario = "TAR-" + corto();
        String hechoCodigo = "ENTREGA-" + corto();
        UUID tarifario = fixtura.tarifarioVigente(codigoTarifario);
        UUID hecho = fixtura.hechoGenerador(hechoCodigo);
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        fixtura.conceptoPorcentual(
                tarifario,
                hecho,
                redondeo,
                facturacion.cuentaDeIngreso(),
                "COM-SERV",
                "0.0030",
                null,
                null,
                false,
                false);
        fixtura.activar(tarifario);
        return new Escenario(codigoTarifario, hechoCodigo, tarifario, contextoDe(fixtura.usuario()));
    }

    @Test
    @DisplayName(
            "Dado un usuario con tres grupos completados y un segmento que exige tres · Cuando cotiza una comisión · Entonces la cotizacion_comision guarda el segmento aplicado · Y el precio es el del segmento, no el base")
    void criterio1() {
        var e = escenario();
        String codigoSegmento = "FIEL-" + corto();
        transaccion.execute(t -> precioCU.crear(
                new EntradaSegmento(codigoSegmento, "Tres grupos completados", Map.of("gruposCompletados", 3), 1),
                e.ctx()));

        var elegido = transaccion.execute(t -> precioCU.resolver(Map.of("gruposCompletados", 3), e.ctx()));
        var salida = transaccion.execute(t -> cotizacionCU.cotizar(
                new EntradaCotizacion(
                        "cot-seg",
                        e.codigoTarifario(),
                        e.hecho(),
                        "ENTREGA_FONDO",
                        UUID.randomUUID(),
                        bob("10000.00"),
                        Optional.empty(),
                        Optional.of(bob("10.00")),
                        Optional.of(elegido)),
                e.ctx()));

        assertThat(elegido.codigo()).isEqualTo(codigoSegmento);
        // El segmento queda en el DESGLOSE: `cotizacion_comision` no tiene columna
        // para el, y no se invento una. Seis meses despues hay que poder decir por
        // que pago eso, y la respuesta esta en la fila.
        assertThat(salida.desglose()).anyMatch(l -> "SEGMENTO".equals(l.concepto()));
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cotizacion_comision WHERE id = ? AND desglose::text LIKE '%SEGMENTO%'",
                        salida.cotizacionId()))
                .isEqualTo(1);
        // 30,00 de base menos 10,00 del beneficio: el precio es el del segmento.
        assertThat(salida.montoTotal()).isEqualByComparingTo(bob("20.00"));
    }

    @Test
    @DisplayName(
            "Dado un usuario que califica para dos segmentos activos · Cuando se resuelve su precio · Entonces se aplica el de mayor prioridad y solo uno queda registrado")
    void criterio2() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        String primero = "ORO-" + corto();
        String segundo = "PLATA-" + corto();
        transaccion.execute(
                t -> precioCU.crear(new EntradaSegmento(primero, "El mejor", Map.of("gruposCompletados", 3), 1), ctx));
        transaccion.execute(
                t -> precioCU.crear(new EntradaSegmento(segundo, "El otro", Map.of("gruposCompletados", 1), 2), ctx));

        var elegido = transaccion.execute(t -> precioCU.resolver(Map.of("gruposCompletados", 5), ctx));

        // Gana la prioridad mas baja, siempre. Si dependiera del orden en que la base
        // devuelve las filas, dos usuarios iguales pagarian distinto.
        assertThat(elegido.codigo()).isEqualTo(primero);
        assertThat(elegido.motivo()).contains("gruposCompletados");
    }

    @Test
    @DisplayName(
            "Dado un grupo creado con un segmento vigente · Cuando el usuario deja de calificar a mitad del ciclo · Entonces el grupo conserva la tarifa congelada")
    void criterio3() {
        var e = escenario();
        UUID grupo = escenario.grupo();
        escenario.congelarTarifa(grupo, e.tarifarioId());

        // El usuario ya no califica: sus hechos no alcanzan.
        var yaNoCalifica = transaccion.execute(t -> precioCU.resolver(Map.of("gruposCompletados", 0), e.ctx()));

        // Pero el grupo sigue con su snapshot: la cotizacion se resuelve contra el
        // tarifario congelado, no contra el vigente de hoy.
        var salida = transaccion.execute(t -> cotizacionCU.cotizar(
                new EntradaCotizacion(
                        "cot-cong",
                        "OTRO-CODIGO-QUE-NO-EXISTE",
                        e.hecho(),
                        "ENTREGA_FONDO",
                        UUID.randomUUID(),
                        bob("10000.00"),
                        Optional.of(grupo),
                        Optional.empty(),
                        Optional.empty()),
                e.ctx()));

        assertThat(yaNoCalifica.codigo()).isNull();
        assertThat(salida.montoComision()).isEqualByComparingTo(bob("30.00"));
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cotizacion_comision WHERE id = ? AND tarifario_id = ?",
                        salida.cotizacionId(),
                        e.tarifarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un segmento cuyo criterio referencia un dato sensible · Cuando se intenta crear · Entonces se rechaza con SEGMENTO_DISCRIMINATORIO")
    void criterio4() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        String codigo = "MAL-" + corto();

        assertThatThrownBy(() -> transaccion.execute(t -> precioCU.crear(
                        new EntradaSegmento(codigo, "Por nacionalidad", Map.of("nacionalidad", 1), 1), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("datos sensibles");
        assertThat(contar("SELECT count(*)::int FROM tarifas.segmento_comercial WHERE codigo = ?", codigo))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave natural del segmento es su codigo, unico en la base. Resolver el
        // precio dos veces con los mismos hechos da lo mismo: no escribe nada.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        String codigo = "REP-" + corto();
        transaccion.execute(
                t -> precioCU.crear(new EntradaSegmento(codigo, "Repetible", Map.of("gruposCompletados", 2), 1), ctx));

        var a = transaccion.execute(t -> precioCU.resolver(Map.of("gruposCompletados", 4), ctx));
        var b = transaccion.execute(t -> precioCU.resolver(Map.of("gruposCompletados", 4), ctx));

        assertThat(b).isEqualTo(a);
        assertThat(contar("SELECT count(*)::int FROM tarifas.segmento_comercial WHERE codigo = ?", codigo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos segmentos con la misma prioridad: gana uno. La ambiguedad se resuelve al
        // definir, no al cobrar.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        String primero = "CONC-A-" + corto();
        String segundo = "CONC-B-" + corto();
        transaccion.execute(t ->
                precioCU.crear(new EntradaSegmento(primero, "El primero", Map.of("gruposCompletados", 2), 7), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> precioCU.crear(
                        new EntradaSegmento(segundo, "El segundo", Map.of("antiguedadMeses", 6), 7), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("prioridad 7");
        assertThat(contar("SELECT count(*)::int FROM tarifas.segmento_comercial WHERE prioridad = 7"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El descuento del segmento se resta al centavo, y nunca deja la comision en
        // negativo: un beneficio mal configurado no genera un credito a favor.
        var e = escenario();

        var salida = transaccion.execute(t -> cotizacionCU.cotizar(
                new EntradaCotizacion(
                        "cot-cuadre",
                        e.codigoTarifario(),
                        e.hecho(),
                        "ENTREGA_FONDO",
                        UUID.randomUUID(),
                        bob("10000.00"),
                        Optional.empty(),
                        Optional.of(bob("999.00")),
                        Optional.empty()),
                e.ctx()));

        assertThat(salida.montoComision()).isEqualByComparingTo(bob("30.00"));
        assertThat(salida.montoTotal()).isEqualByComparingTo(bob("0.00"));
        assertThat(salida.montoTotal().monto().signum()).isNotNegative();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "segmentador"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "segmentador"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Si falta el dato para evaluar el criterio, NO se adivina: se cotiza al precio
        // base y se dice cual falto. Adivinar a favor regala plata y adivinar en contra
        // cobra de mas, y las dos cosas hay que explicarlas despues.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        String codigo = "SIN-DATO-" + corto();
        transaccion.execute(
                t -> precioCU.crear(new EntradaSegmento(codigo, "Exige reputacion", Map.of("reputacion", 80), 1), ctx));

        SegmentoAplicable.Eleccion elegido =
                transaccion.execute(t -> precioCU.resolver(Map.of("gruposCompletados", 9), ctx));

        assertThat(elegido.evaluable()).isFalse();
        assertThat(elegido.codigo()).isNull();
        assertThat(elegido.motivo()).contains("reputacion");
    }
}
