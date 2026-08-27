package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.ClasificacionPep.NivelRiesgo;
import bo.aportaya.cumplimiento.dominio.NivelDeDiligencia;
import bo.aportaya.cumplimiento.dominio.PeriodicidadDeRevision;
import bo.aportaya.cumplimiento.dominio.RequisitosDeNivel;
import bo.aportaya.cumplimiento.infraestructura.CalificacionRiesgoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.CasoLftRepositorio;
import bo.aportaya.cumplimiento.infraestructura.DeclaracionPepRepositorio;
import bo.aportaya.cumplimiento.infraestructura.DiligenciaRepositorio;
import bo.aportaya.cumplimiento.infraestructura.LimiteRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ClaveIdempotencia;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-02 · Elevar el nivel de debida diligencia.
 *
 * <p>Subir de nivel es lo que desbloquea topes mas altos, asi que la puerta tiene que
 * ser exactamente igual de dura que los topes que abre. Cuatro razones la cierran:
 * documentacion incompleta, un PEP con una sola firma, una coincidencia de lista sin
 * resolver, y un destino que no es superior al actual.
 *
 * <p>Nunca baja de nivel. Bajar por la via de «elevar» dejaria a alguien con los
 * topes de un nivel que ya no le corresponde sin que nadie lo haya decidido.
 */
@Service
public class CU02ElevarDiligencia {

    private static final String SIN_DOCUMENTOS = "[]";

    private final Datos datos;
    private final DiligenciaRepositorio diligencias;
    private final CalificacionRiesgoRepositorio calificaciones;
    private final DeclaracionPepRepositorio declaraciones;
    private final CasoLftRepositorio casos;
    private final LimiteRepositorio limites;
    private final Outbox outbox;
    private final Reloj reloj;
    private final RequisitosDeNivel requisitos;
    private final PeriodicidadDeRevision periodicidad;

    public CU02ElevarDiligencia(
            Datos datos,
            DiligenciaRepositorio diligencias,
            CalificacionRiesgoRepositorio calificaciones,
            DeclaracionPepRepositorio declaraciones,
            CasoLftRepositorio casos,
            LimiteRepositorio limites,
            Outbox outbox,
            Reloj reloj,
            RequisitosDeNivel requisitos,
            PeriodicidadDeRevision periodicidad) {
        this.datos = datos;
        this.diligencias = diligencias;
        this.calificaciones = calificaciones;
        this.declaraciones = declaraciones;
        this.casos = casos;
        this.limites = limites;
        this.outbox = outbox;
        this.reloj = reloj;
        this.requisitos = requisitos;
        this.periodicidad = periodicidad;
    }

    @Transactional
    public SalidaDiligencia ejecutar(EntradaDiligencia entrada, ContextoSesion ctx) {
        NivelDeDiligencia destino = NivelDeDiligencia.valueOf(entrada.nivelDestino());
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU02-04. Se compara contra la calificacion vigente, que es la que
            // manda: el tipo de la diligencia puede ir por detras mientras se tramita.
            NivelDeDiligencia actual = calificaciones
                    .vigenteDe(dsl, entrada.usuarioId())
                    .map(c -> NivelDeDiligencia.valueOf(c.nivelDdRequerido()))
                    .orElse(NivelDeDiligencia.SIMPLIFICADA);
            if (!destino.esSuperiorA(actual)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(2, 4),
                        "El nivel " + destino + " no es superior al actual (" + actual + "): esto no eleva nada.");
            }

            // AP-CU02-03. Una coincidencia sin resolver congela el ascenso: subirle
            // los topes a quien esta bajo investigacion es exactamente al reves.
            if (casos.hayCasoAbiertoDe(dsl, entrada.usuarioId())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(2, 3),
                        "Hay una coincidencia de lista sin resolver: el nivel no sube hasta que se cierre.");
            }

            // AP-CU02-01.
            List<String> faltantes = requisitos.faltantes(destino, entrada.tiposDeDocumento());
            if (!faltantes.isEmpty()) {
                UUID observada = abrirOElevar(dsl, entrada.usuarioId(), destino, "OBSERVADA", ahora);
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "cumplimiento.diligencia_observada",
                                "debida_diligencia",
                                observada,
                                Map.of(
                                        "usuarioId", entrada.usuarioId().toString(),
                                        "faltantes", String.join(",", faltantes)),
                                UUID.fromString(ctx.traza().id())));
                return new SalidaDiligencia(observada, "OBSERVADA", faltantes, List.of());
            }

            // AP-CU02-02 · R-UIF-10. Se comprueba aca para dar un mensaje util, y la
            // base lo vuelve a comprobar con tg_ddd_pep: si divergieran, gana la base.
            boolean esPep = declaraciones.esPepVigente(dsl, entrada.usuarioId());
            if (esPep && entrada.segundaRevisionPor().isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(2, 2),
                        "Un PEP necesita dos revisores distintos: falta la segunda revision independiente.");
            }

            UUID diligenciaId = abrirOElevar(dsl, entrada.usuarioId(), destino, "EN_PROCESO", ahora);
            diligencias.completar(dsl, diligenciaId, entrada.aprobadaPor(), entrada.segundaRevisionPor(), ahora);

            // R-UIF-11: cerrar antes de abrir, siempre en ese orden.
            NivelRiesgo riesgo = esPep ? NivelRiesgo.ALTO : NivelRiesgo.MEDIO;
            calificaciones.cerrarVigente(dsl, entrada.usuarioId(), ahora);
            calificaciones.calificar(
                    dsl,
                    entrada.usuarioId(),
                    riesgo.name(),
                    destino.name(),
                    periodicidad.mesesPara(riesgo),
                    BigDecimal.ZERO,
                    Optional.of(entrada.aprobadaPor()),
                    "Elevacion a " + destino,
                    ahora);

            List<TopeNuevo> nuevos = limites.vigentesPara(dsl, destino.name(), ahora.toLocalDate()).stream()
                    .map(t -> new TopeNuevo(
                            t.concepto(),
                            t.montoMaximo() == null ? null : t.montoMaximo().toPlainString(),
                            t.ventana()))
                    .toList();

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.diligencia_elevada",
                            "debida_diligencia",
                            diligenciaId,
                            Map.of(
                                    "usuarioId", entrada.usuarioId().toString(),
                                    "nivel", destino.name(),
                                    "nivelRiesgo", riesgo.name()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDiligencia(diligenciaId, "COMPLETA", List.of(), nuevos);
        });
    }

    private UUID abrirOElevar(
            org.jooq.DSLContext dsl, UUID usuarioId, NivelDeDiligencia destino, String estado, OffsetDateTime ahora) {
        var existente = diligencias.vigenteDe(dsl, usuarioId);
        if (existente.isPresent()) {
            diligencias.elevarTipo(dsl, usuarioId, destino.name());
            return existente.get().id();
        }
        return diligencias.abrir(dsl, usuarioId, destino.name(), estado, SIN_DOCUMENTOS, SIN_DOCUMENTOS, ahora);
    }

    public record EntradaDiligencia(
            ClaveIdempotencia clave,
            UUID usuarioId,
            String nivelDestino,
            List<String> tiposDeDocumento,
            UUID aprobadaPor,
            Optional<UUID> segundaRevisionPor) {}

    public record SalidaDiligencia(
            UUID diligenciaId, String estado, List<String> faltantes, List<TopeNuevo> limitesNuevos) {}

    public record TopeNuevo(String concepto, String monto, String ventana) {}
}
