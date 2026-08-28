package bo.aportaya.erp.aplicacion;

import bo.aportaya.erp.dominio.CuadreContable;
import bo.aportaya.erp.infraestructura.PeriodoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-100 · Abrir y cerrar el periodo contable.
 *
 * <p>**En un periodo cerrado no se asienta nada** (R-CTB-01). Es lo que hace que un
 * balance publicado siga diciendo lo mismo dentro de un año: sin esa puerta, cualquiera
 * podria agregar un asiento con fecha vieja y cambiar un estado financiero ya entregado.
 *
 * <p>Y **si no cuadra, no es un cierre**. El cierre guarda el debe y el haber del
 * momento, y {@code ck_cierre_periodo_cuadrado} los exige iguales. Cerrar con
 * diferencia seria firmar que las cuentas estan bien cuando no lo estan.
 */
@Service
public class CU100AbrirCerrarPeriodo {

    private final Datos datos;
    private final PeriodoRepositorio periodos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU100AbrirCerrarPeriodo(Datos datos, PeriodoRepositorio periodos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.periodos = periodos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    /** Abre los doce periodos del ejercicio de una vez: un calendario a medias se olvida. */
    @Transactional
    public SalidaEjercicio abrirEjercicio(int anio, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            if (periodos.ejercicio(dsl, anio).isPresent()) {
                throw new ErrorDeNegocio(CodigoError.de(100, 1), "El ejercicio " + anio + " ya esta abierto.");
            }
            UUID ejercicioId = periodos.abrirEjercicio(dsl, anio, LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31));

            for (int mes = 1; mes <= 12; mes++) {
                YearMonth ym = YearMonth.of(anio, mes);
                periodos.abrirPeriodo(dsl, ejercicioId, mes, ym.atDay(1), ym.atEndOfMonth());
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.ejercicio_abierto",
                            "ejercicio_fiscal",
                            ejercicioId,
                            Map.of("anio", Integer.toString(anio), "periodos", "12"),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaEjercicio(ejercicioId, anio, 12);
        });
    }

    @Transactional
    public SalidaCierre cerrarPeriodo(EntradaCierre entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var periodo = periodos.periodoPorId(dsl, entrada.periodoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(100, 1), "Ese periodo no existe."));
            // AP-CU100-01.
            if (!"ABIERTO".equals(periodo.estado())) {
                throw new ErrorDeNegocio(CodigoError.de(100, 1), "El periodo " + periodo.mes() + " ya esta cerrado.");
            }
            // AP-CU100-02 · los periodos se cierran en orden. Cerrar marzo con febrero
            // abierto deja un hueco que despues nadie puede explicar: el balance de
            // marzo incluiria asientos que febrero todavia puede recibir.
            var anterior = periodos.primerPeriodoAbierto(dsl, periodo.ejercicioId());
            if (anterior.isPresent() && anterior.get().mes() < periodo.mes()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(100, 2),
                        "El periodo %d sigue abierto: los periodos se cierran en orden."
                                .formatted(anterior.get().mes()));
            }

            var totales = periodos.totalesDe(dsl, entrada.periodoId());
            var cuadre = CuadreContable.verificar(totales.debe(), totales.haber());
            // AP-CU100-04. Se dice POR CUANTO y de que lado: un «no cuadra» a secas
            // obliga a buscar la diferencia a mano entre miles de asientos.
            if (!cuadre.cuadra()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(100, 3),
                        "El periodo no cuadra: debe %s, haber %s, diferencia %s."
                                .formatted(
                                        cuadre.totalDebe().toPlainString(),
                                        cuadre.totalHaber().toPlainString(),
                                        cuadre.diferencia().toPlainString()));
            }

            UUID cierreId = periodos.cerrar(
                    dsl,
                    entrada.periodoId(),
                    ctx.usuarioId(),
                    cuadre.totalDebe(),
                    cuadre.totalHaber(),
                    entrada.observaciones(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.periodo_cerrado",
                            "periodo_contable",
                            entrada.periodoId(),
                            Map.of(
                                    "mes", Integer.toString(periodo.mes()),
                                    "totalDebe", cuadre.totalDebe().toPlainString(),
                                    "totalHaber", cuadre.totalHaber().toPlainString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCierre(cierreId, entrada.periodoId(), cuadre.totalDebe(), cuadre.totalHaber(), true);
        });
    }

    /**
     * **No hay reapertura de periodos, y no es un olvido.**
     *
     * <p>{@code cierre_periodo_contable} es append-only (R-AUD-01) y tiene un unico por
     * {@code periodo_contable_id}: la constancia del cierre no se puede reescribir ni
     * duplicar. Reabrir el periodo dejaria su estado en ABIERTO con una constancia que
     * dice otra cosa, y el segundo cierre seria imposible — el periodo quedaria en un
     * estado del que no puede salir.
     *
     * <p>Corregir un mes cerrado se hace como se corrige todo en contabilidad: con un
     * asiento en el periodo siguiente. Queda declarado como hueco en
     * {@code planes/informes/carril-3D-erp.md}.
     */
    public record EntradaCierre(UUID periodoId, String observaciones) {}

    public record SalidaEjercicio(UUID ejercicioId, int anio, int periodosAbiertos) {}

    public record SalidaCierre(
            UUID cierreId,
            UUID periodoId,
            java.math.BigDecimal totalDebe,
            java.math.BigDecimal totalHaber,
            boolean cuadrado) {}
}
