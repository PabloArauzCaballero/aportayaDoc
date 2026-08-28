package bo.aportaya.publicidad.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.publicidad.dominio.LimiteDeGasto;
import bo.aportaya.publicidad.infraestructura.AnuncianteRepositorio;
import bo.aportaya.publicidad.infraestructura.CampanaRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-111 · Crear y aprobar una campana publicitaria.
 *
 * <p>**Nada sale al aire sin que alguien lo apruebe.** La campana nace en revision y
 * solo la aprobacion de Operaciones deja entregar a sus conjuntos. Sin ese paso, un
 * anunciante podria poner cualquier mensaje delante de todos los usuarios de la app
 * apretando un boton.
 *
 * <p>El limite de gasto se comprueba **al aprobar y no al crear**: entre una cosa y la
 * otra pueden pasar dias, y lo que importa es cuanto queda cuando la campana va a
 * empezar a gastar. La cuenta se lee con {@code FOR UPDATE} para que dos aprobaciones
 * simultaneas no lean el mismo saldo.
 */
@Service
public class CU111CrearCampana {

    private final Datos datos;
    private final CampanaRepositorio campanas;
    private final AnuncianteRepositorio anunciantes;
    private final Outbox outbox;

    public CU111CrearCampana(
            Datos datos, CampanaRepositorio campanas, AnuncianteRepositorio anunciantes, Outbox outbox) {
        this.datos = datos;
        this.campanas = campanas;
        this.anunciantes = anunciantes;
        this.outbox = outbox;
    }

    @Transactional
    public Salida crear(Entrada entrada, ContextoSesion ctx) {
        if (entrada.conjuntos().isEmpty()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(111, 2), "Una campana sin conjuntos no entrega nada: no se crea vacia.");
        }
        return datos.conContexto(ctx, dsl -> {
            var cuenta = anunciantes
                    .cuentaBloqueada(dsl, entrada.cuentaPublicitariaId())
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(111, 3), "Esa cuenta publicitaria no existe."));
            // AP-CU111-03 · denegar por omision (invariante 9).
            if (!"ACTIVA".equals(cuenta.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(111, 3),
                        "La cuenta publicitaria esta " + cuenta.estado() + ": no admite campanas nuevas.");
            }

            for (Conjunto conjunto : entrada.conjuntos()) {
                var espacio = campanas.espacio(dsl, conjunto.espacioPublicitarioId())
                        .orElseThrow(() ->
                                new ErrorDeNegocio(CodigoError.de(111, 2), "Ese espacio publicitario no existe."));
                // AP-CU111-02 · un espacio apagado no se enciende para una campana.
                if (!Boolean.TRUE.equals(espacio.activo())) {
                    throw new ErrorDeNegocio(
                            CodigoError.de(111, 2), "El espacio " + espacio.codigo() + " esta inactivo: no entrega.");
                }
                if (!campanas.existeSegmento(dsl, conjunto.segmentoAudienciaId())) {
                    throw new ErrorDeNegocio(CodigoError.de(111, 2), "Ese segmento de audiencia no existe.");
                }
            }

            UUID campanaId = campanas.crear(
                    dsl,
                    entrada.cuentaPublicitariaId(),
                    entrada.nombre(),
                    entrada.objetivo(),
                    entrada.presupuestoTotal(),
                    entrada.moneda(),
                    entrada.fechaInicio(),
                    entrada.fechaFin());

            for (Conjunto conjunto : entrada.conjuntos()) {
                campanas.agregarConjunto(
                        dsl,
                        campanaId,
                        conjunto.segmentoAudienciaId(),
                        conjunto.espacioPublicitarioId(),
                        conjunto.nombre(),
                        conjunto.presupuestoDiario(),
                        entrada.moneda(),
                        conjunto.pujaMaxima(),
                        conjunto.modeloPuja());
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.campana_en_revision",
                            "campana_publicitaria",
                            campanaId,
                            Map.of(
                                    "presupuestoTotal",
                                            entrada.presupuestoTotal().toPlainString(),
                                    "conjuntos",
                                            String.valueOf(entrada.conjuntos().size())),
                            UUID.fromString(ctx.traza().id())));

            return new Salida(campanaId, "EN_REVISION", entrada.presupuestoTotal());
        });
    }

    @Transactional
    public Salida aprobar(UUID campanaId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var campana = campanas.bloqueada(dsl, campanaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(111, 2), "Esa campana no existe."));
            if (!"EN_REVISION".equals(campana.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(111, 2),
                        "La campana esta " + campana.estado() + ": solo se aprueba lo que esta en revision.");
            }
            var cuenta = anunciantes
                    .cuentaBloqueada(dsl, campana.cuentaPublicitariaId())
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(111, 3), "Esa cuenta publicitaria no existe."));
            if (!"ACTIVA".equals(cuenta.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(111, 3),
                        "La cuenta publicitaria esta " + cuenta.estado() + ": no puede tomar mas gasto.");
            }

            // AP-CU111-01 · R-PUB-02: lo que decide es cuanto QUEDA del mes, no el limite.
            if (!LimiteDeGasto.cabe(
                    campana.presupuestoTotal(), cuenta.limiteGastoMensual(), cuenta.saldoConsumidoMes())) {
                BigDecimal queda = LimiteDeGasto.disponible(cuenta.limiteGastoMensual(), cuenta.saldoConsumidoMes())
                        .orElse(BigDecimal.ZERO);
                throw new ErrorDeNegocio(
                        CodigoError.de(111, 1),
                        "El presupuesto (%s) excede lo disponible del mes (%s)."
                                .formatted(campana.presupuestoTotal().toPlainString(), queda.toPlainString()));
            }

            campanas.aprobar(dsl, campanaId, ctx.usuarioId());
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.campana_aprobada",
                            "campana_publicitaria",
                            campanaId,
                            Map.of(
                                    "aprobadaPor", ctx.usuarioId().toString(),
                                    "presupuestoTotal",
                                            campana.presupuestoTotal().toPlainString()),
                            UUID.fromString(ctx.traza().id())));
            return new Salida(campanaId, "ACTIVA", campana.presupuestoTotal());
        });
    }

    @Transactional
    public Salida rechazar(UUID campanaId, String motivo, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var campana = campanas.bloqueada(dsl, campanaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(111, 2), "Esa campana no existe."));
            if (!campanas.rechazar(dsl, campanaId)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(111, 2),
                        "La campana esta " + campana.estado() + ": solo se rechaza lo que esta en revision.");
            }
            // El motivo va en el evento: la tabla no tiene columna donde guardarlo.
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.campana_rechazada",
                            "campana_publicitaria",
                            campanaId,
                            Map.of("motivo", motivo == null ? "" : motivo),
                            UUID.fromString(ctx.traza().id())));
            return new Salida(campanaId, "RECHAZADA", campana.presupuestoTotal());
        });
    }

    public record Conjunto(
            UUID segmentoAudienciaId,
            UUID espacioPublicitarioId,
            String nombre,
            BigDecimal presupuestoDiario,
            BigDecimal pujaMaxima,
            String modeloPuja) {}

    public record Entrada(
            UUID cuentaPublicitariaId,
            String nombre,
            String objetivo,
            BigDecimal presupuestoTotal,
            String moneda,
            OffsetDateTime fechaInicio,
            OffsetDateTime fechaFin,
            List<Conjunto> conjuntos) {}

    public record Salida(UUID campanaPublicitariaId, String estado, BigDecimal presupuestoTotal) {}
}
