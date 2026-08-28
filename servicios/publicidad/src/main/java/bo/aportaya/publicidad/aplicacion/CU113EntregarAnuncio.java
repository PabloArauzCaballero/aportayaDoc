package bo.aportaya.publicidad.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.publicidad.dominio.SubastaDelEspacio;
import bo.aportaya.publicidad.infraestructura.CampanaRepositorio;
import bo.aportaya.publicidad.infraestructura.EntregaRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-113 · Entregar un anuncio y medir su desempeno.
 *
 * <p>**Nunca se entrega por encima del presupuesto autorizado.** Un anuncio cuyo
 * conjunto ya gasto lo del dia no compite, aunque puje mas que todos. Cuando no queda
 * ninguno elegible, el espacio no muestra publicidad — y eso no es un error: es el
 * comportamiento correcto, y por eso {@code anuncioId} viaja nulo en vez de fallar.
 *
 * <p>El gasto del dia no se guarda en una columna: se suma de las impresiones y los
 * clics. Una columna contador seria un candado sobre el conjunto, y aca las entregas
 * son concurrentes por definicion. El bloqueo va sobre la fila del conjunto que compite,
 * no sobre la campana entera.
 */
@Service
public class CU113EntregarAnuncio {

    private final Datos datos;
    private final EntregaRepositorio entregas;
    private final CampanaRepositorio campanas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU113EntregarAnuncio(
            Datos datos, EntregaRepositorio entregas, CampanaRepositorio campanas, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.entregas = entregas;
        this.campanas = campanas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    /** Programa un anuncio: la pieza tiene que estar aprobada (R-PUB-04). */
    @Transactional
    public UUID programar(UUID conjuntoId, UUID piezaId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> entregas.programarAnuncio(dsl, conjuntoId, piezaId));
    }

    @Transactional
    public Salida entregar(Entrada entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            var espacio = campanas.espacio(dsl, entrada.espacioPublicitarioId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(113, 1), "Ese espacio no existe."));
            if (!Boolean.TRUE.equals(espacio.activo())) {
                return Salida.sinAnuncio("ESPACIO_INACTIVO");
            }
            // AP-CU113-02 · el espacio tiene cupo finito y no se estira.
            if (entregas.enEntrega(dsl, espacio.id()) >= espacio.capacidadMaximaSimultanea()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(113, 2),
                        "El espacio %s ya esta en su capacidad maxima.".formatted(espacio.codigo()));
            }

            var candidatos = entregas.candidatos(dsl, espacio.id(), ahora.toLocalDate());
            var ganador = SubastaDelEspacio.ganador(candidatos);
            if (ganador.isEmpty()) {
                // AP-CU113-01 · no es un error de cliente: el espacio no muestra nada.
                agotarLosQueNoLlegan(dsl, candidatos);
                return Salida.sinAnuncio("SIN_ANUNCIO_ELEGIBLE");
            }

            var elegido = ganador.get();
            var origen = entregas.origenDelConjunto(dsl, elegido.conjuntoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(113, 1), "Ese conjunto no existe."));

            entregas.marcarEnEntrega(dsl, elegido.anuncioId(), ahora);
            BigDecimal costo = elegido.costoDeLaImpresion();
            UUID impresionId = entregas.registrarImpresion(
                    dsl, elegido.anuncioId(), entrada.usuarioId(), costo, origen.moneda(), ahora);

            cargar(dsl, origen, costo);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.impresion_registrada",
                            "impresion_anuncio",
                            impresionId,
                            Map.of(
                                    "anuncioId", elegido.anuncioId().toString(),
                                    "costo", costo.toPlainString()),
                            UUID.fromString(ctx.traza().id())));

            return new Salida(elegido.anuncioId(), impresionId, costo, null);
        });
    }

    @Transactional
    public SalidaClic registrarClic(UUID impresionId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            var origen = entregas.origenDeLaImpresion(dsl, impresionId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(113, 1), "Esa impresion no existe."));
            BigDecimal costo = SubastaDelEspacio.costoDeClic(origen.modeloPuja(), origen.pujaMaxima());
            UUID clicId = entregas.registrarClic(dsl, impresionId, ctx.usuarioId(), costo, origen.moneda(), ahora);
            cargar(dsl, origen, costo);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.clic_registrado",
                            "clic_anuncio",
                            clicId,
                            Map.of(
                                    "impresionId", impresionId.toString(),
                                    "costo", costo.toPlainString()),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaClic(clicId, costo);
        });
    }

    @Transactional
    public UUID registrarConversion(UUID clicId, UUID impresionId, String tipo, UUID referenciaId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            UUID id = entregas.registrarConversion(dsl, clicId, impresionId, tipo, referenciaId, ahora);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.conversion_registrada",
                            "conversion_anuncio",
                            id,
                            Map.of("tipo", tipo),
                            UUID.fromString(ctx.traza().id())));
            return id;
        });
    }

    /**
     * Carga el costo a la campana y a la cuenta, y detiene lo que se quedo sin plata.
     *
     * <p>Cuando la campana llega a su presupuesto total, no se agota el dia: se
     * finaliza entera. Es la diferencia entre «hoy no muestro mas» y «esta campana
     * termino».
     */
    private void cargar(org.jooq.DSLContext dsl, EntregaRepositorio.Origen origen, BigDecimal costo) {
        if (costo.signum() == 0) {
            return;
        }
        campanas.sumarConsumo(dsl, origen.campanaId(), costo);
        var campana = campanas.bloqueada(dsl, origen.campanaId());
        campana.ifPresent(c -> {
            if (c.presupuestoConsumido().compareTo(c.presupuestoTotal()) >= 0) {
                campanas.finalizar(dsl, c.id());
            }
        });
    }

    /** Los conjuntos que ya no pueden pagar ni una impresion mas dejan de estar ACTIVOS. */
    private void agotarLosQueNoLlegan(org.jooq.DSLContext dsl, java.util.List<SubastaDelEspacio.Candidato> candidatos) {
        candidatos.stream()
                .filter(c -> !c.puedeEntregar())
                .map(SubastaDelEspacio.Candidato::conjuntoId)
                .distinct()
                .forEach(conjuntoId -> campanas.agotarConjunto(dsl, conjuntoId));
    }

    public record Entrada(UUID espacioPublicitarioId, UUID usuarioId) {}

    public record Salida(UUID anuncioId, UUID impresionId, BigDecimal costo, String motivo) {

        static Salida sinAnuncio(String motivo) {
            return new Salida(null, null, BigDecimal.ZERO, motivo);
        }

        public Optional<UUID> anuncio() {
            return Optional.ofNullable(anuncioId);
        }
    }

    public record SalidaClic(UUID clicId, BigDecimal costo) {}
}
