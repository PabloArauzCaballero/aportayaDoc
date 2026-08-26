package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.dominio.CuadrarPartidas;
import bo.aportaya.nucleofinanciero.dominio.OrigenAsiento;
import bo.aportaya.nucleofinanciero.dominio.Partida;
import bo.aportaya.nucleofinanciero.infraestructura.AsientoRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaContableRepositorio;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

/**
 * CU-24 · Registrar el asiento contable de una operación.
 *
 * <p>No abre su propia transacción ni su propio {@code conContexto}: por diseño del
 * caso de uso ("en la misma transacción que el hecho económico"), el organismo que
 * cobra, entrega o devenga ya abrió las dos cosas, y le pasa el {@link DSLContext} en
 * curso. Esta clase vive igual en {@code aplicacion/} porque orquesta piezas hacia un
 * objetivo completo — la ArchUnit de esta capa exige {@code @Transactional} solo
 * cuando la clase abre transacción, no a toda clase que viva acá.
 *
 * <p><b>El saldo no se escribe acá.</b> Lo deriva el motor desde
 * {@code movimiento_contable} (R-CTB-09), igual que el de billetera desde R-BIL-16:
 * el organismo inserta el libro y la base mantiene la caché.
 *
 * <p>Todavía no tiene quien la llame: el circuito del dinero (billetera, aportes,
 * entregas) es de las olas 2 a 4. Este carril entrega el organismo completo, probado
 * de forma aislada, para que esas olas lo consuman sin negociar su forma.
 */
@Service
public class CU24RegistrarAsiento {

    private static final int LARGO_COLUMNA_DESCRIPCION = 160; // VARCHAR(160), no es umbral de negocio

    private final CuentaContableRepositorio cuentas;
    private final AsientoRepositorio asientos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU24RegistrarAsiento(
            CuentaContableRepositorio cuentas, AsientoRepositorio asientos, Outbox outbox, Reloj reloj) {
        this.cuentas = cuentas;
        this.asientos = asientos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    public SalidaAsiento ejecutar(DSLContext dsl, EntradaAsiento entrada, ContextoSesion ctx) {
        CuadrarPartidas.Totales totales = CuadrarPartidas.verificar(entrada.partidas());

        List<PartidaResuelta> resueltas =
                entrada.partidas().stream().map(p -> resolver(dsl, p)).toList();

        AsientoRepositorio.AsientoCreado asiento = asientos.crear(
                dsl,
                reloj.ahora().atOffset(ZoneOffset.UTC),
                entrada.glosa(),
                entrada.origenTipo().name(),
                entrada.origenId(),
                entrada.grupoId(),
                registrante(ctx),
                Optional.empty());

        String descripcion = descripcionDe(entrada.glosa());
        for (PartidaResuelta r : resueltas) {
            asientos.agregarMovimiento(dsl, asiento.id(), r.cuentaId(), r.debe(), r.haber(), descripcion);
        }

        outbox.emitir(
                dsl,
                new EventoDominio(
                        "nucleo_financiero.asiento_registrado",
                        "asiento_contable",
                        asiento.id(),
                        Map.of(
                                "origenTipo", entrada.origenTipo().name(),
                                "origenId", entrada.origenId().toString()),
                        UUID.fromString(ctx.traza().id())));

        return new SalidaAsiento(asiento.id(), asiento.numero(), totales.debe(), totales.haber());
    }

    /**
     * CU-24 · flujo alternativo "Corrección de un asiento": no se edita, se crea el
     * inverso, marcado {@code REVERSADO} y enlazado por {@code asiento_reversa_id}
     * (R-AUD-06, R-AUD-11). El inverso también tiene que cuadrar, y el trigger de
     * R-AUD-05 se lo exige.
     */
    public SalidaAsiento reversar(DSLContext dsl, UUID asientoOriginalId, String motivo, ContextoSesion ctx) {
        AsientoRepositorio.AsientoExistente original = asientos.porId(dsl, asientoOriginalId)
                .orElseThrow(() -> new ErrorDeNegocio(
                        CodigoError.de(24, 2),
                        "El asiento a reversar no existe.",
                        Map.of("asientoId", asientoOriginalId)));

        List<AsientoRepositorio.MovimientoExistente> movimientos = asientos.movimientosDe(dsl, asientoOriginalId);
        String glosaReversa = descripcionDe("Reversa: " + motivo);

        AsientoRepositorio.AsientoCreado reversa = asientos.crear(
                dsl,
                reloj.ahora().atOffset(ZoneOffset.UTC),
                glosaReversa,
                original.origenTipo(),
                original.origenId(),
                Optional.ofNullable(original.grupoId()),
                registrante(ctx),
                Optional.of(asientoOriginalId));

        Dinero totalDebe = Dinero.cero(bo.aportaya.plataforma.dominio.Moneda.BOB);
        Dinero totalHaber = totalDebe;
        for (AsientoRepositorio.MovimientoExistente m : movimientos) {
            // El inverso intercambia debe y haber de cada línea, cuenta por cuenta.
            asientos.agregarMovimiento(dsl, reversa.id(), m.cuentaId(), m.haber(), m.debe(), glosaReversa);
            totalDebe = totalDebe.mas(Dinero.de(m.haber(), bo.aportaya.plataforma.dominio.Moneda.BOB));
            totalHaber = totalHaber.mas(Dinero.de(m.debe(), bo.aportaya.plataforma.dominio.Moneda.BOB));
        }

        outbox.emitir(
                dsl,
                new EventoDominio(
                        "nucleo_financiero.asiento_reversado",
                        "asiento_contable",
                        reversa.id(),
                        Map.of("asientoOriginalId", asientoOriginalId.toString()),
                        UUID.fromString(ctx.traza().id())));

        return new SalidaAsiento(reversa.id(), reversa.numero(), totalDebe, totalHaber);
    }

    /** El actor Sistema no deja {@code registrado_por}: no hay una persona detrás del hecho. */
    private Optional<UUID> registrante(ContextoSesion ctx) {
        return ctx.esSistema() ? Optional.empty() : Optional.of(ctx.usuarioId());
    }

    /**
     * {@code movimiento_contable.descripcion} es {@code VARCHAR(160)} y
     * {@code asiento_contable.glosa} es {@code VARCHAR(200)}. La glosa completa queda
     * siempre en el asiento: esto recorta la copia por línea, no el original.
     */
    private String descripcionDe(String glosa) {
        return glosa.length() > LARGO_COLUMNA_DESCRIPCION ? glosa.substring(0, LARGO_COLUMNA_DESCRIPCION) : glosa;
    }

    private PartidaResuelta resolver(DSLContext dsl, Partida p) {
        var cuenta = cuentas.porCodigo(dsl, p.cuentaCodigo())
                .orElseThrow(() -> new ErrorDeNegocio(
                        CodigoError.de(24, 2),
                        "La cuenta «%s» no existe en el plan de cuentas.".formatted(p.cuentaCodigo()),
                        Map.of("cuentaCodigo", p.cuentaCodigo())));
        return new PartidaResuelta(cuenta.id(), p.debe(), p.haber());
    }

    private record PartidaResuelta(UUID cuentaId, BigDecimal debe, BigDecimal haber) {}

    public record EntradaAsiento(
            OrigenAsiento origenTipo, UUID origenId, List<Partida> partidas, String glosa, Optional<UUID> grupoId) {

        /** La forma corta, para los orígenes que no pertenecen a ningún grupo (comisión, ajuste). */
        public EntradaAsiento(OrigenAsiento origenTipo, UUID origenId, List<Partida> partidas, String glosa) {
            this(origenTipo, origenId, partidas, glosa, Optional.empty());
        }
    }

    public record SalidaAsiento(UUID asientoId, long numero, Dinero totalDebe, Dinero totalHaber) {}
}
