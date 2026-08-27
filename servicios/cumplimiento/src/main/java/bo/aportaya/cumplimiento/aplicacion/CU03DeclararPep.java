package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.ClasificacionPep;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep.BeneficiarioFinal;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep.Declaracion;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep.Resultado;
import bo.aportaya.cumplimiento.infraestructura.CalificacionRiesgoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.CasoLftRepositorio;
import bo.aportaya.cumplimiento.infraestructura.DeclaracionPepRepositorio;
import bo.aportaya.cumplimiento.infraestructura.DiligenciaRepositorio;
import bo.aportaya.plataforma.datos.Datos;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-03 · Declaracion PEP y beneficiario final.
 *
 * <p>Tres escrituras en una sola transaccion —declaracion, recalificacion y tipo de
 * diligencia— porque las tres describen el mismo hecho. Una declaracion PEP guardada
 * sin su recalificacion deja a la persona con el riesgo de ayer y el monitoreo de
 * ayer, que es peor que no haberla guardado: aparenta control donde no lo hay.
 */
@Service
public class CU03DeclararPep {

    private static final String DILIGENCIA_REFORZADA = "REFORZADA";
    private static final String SIN_DOCUMENTOS = "[]";

    private final Datos datos;
    private final DeclaracionPepRepositorio declaraciones;
    private final CalificacionRiesgoRepositorio calificaciones;
    private final DiligenciaRepositorio diligencias;
    private final CasoLftRepositorio casos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int mesesRevisionRiesgoAlto;
    private final int diasPlazoCasoLft;

    public CU03DeclararPep(
            Datos datos,
            DeclaracionPepRepositorio declaraciones,
            CalificacionRiesgoRepositorio calificaciones,
            DiligenciaRepositorio diligencias,
            CasoLftRepositorio casos,
            Outbox outbox,
            Reloj reloj,
            // Periodicidad y plazo son politica, no constantes de codigo
            // (invariante 10): se declaran donde se ven y se auditan.
            @Value("${cumplimiento.revision.meses-riesgo-alto:6}") int mesesRevisionRiesgoAlto,
            @Value("${cumplimiento.caso-lft.dias-plazo:30}") int diasPlazoCasoLft) {
        this.datos = datos;
        this.declaraciones = declaraciones;
        this.calificaciones = calificaciones;
        this.diligencias = diligencias;
        this.casos = casos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.mesesRevisionRiesgoAlto = mesesRevisionRiesgoAlto;
        this.diasPlazoCasoLft = diasPlazoCasoLft;
    }

    @Transactional
    public SalidaDeclaracion ejecutar(EntradaDeclaracion entrada, ContextoSesion ctx) {
        Declaracion declaracion = new Declaracion(
                entrada.esPep(),
                entrada.tipoPep().map(ClasificacionPep.TipoPep::valueOf),
                entrada.cargo(),
                entrada.institucion());

        // AP-CU03-01 y AP-CU03-02 se deciden ANTES de abrir nada: si la declaracion
        // no sirve, no puede quedar rastro de que se guardo.
        if (!ClasificacionPep.declaracionCompleta(declaracion)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(3, 1),
                    "Quien declara ser PEP tiene que decir su cargo y la institucion: sin eso no hay nada que cotejar.");
        }
        Optional<BeneficiarioFinal> sinDocumento = ClasificacionPep.primeroSinDocumento(entrada.beneficiariosFinales());
        if (sinDocumento.isPresent()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(3, 2),
                    "Falta identificar al beneficiario final "
                            + sinDocumento.get().nombre() + ".");
        }

        Resultado resultado = ClasificacionPep.clasificar(declaracion, entrada.beneficiariosFinales());
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            UUID declaracionId = declaraciones.declarar(
                    dsl,
                    entrada.usuarioId(),
                    entrada.esPep(),
                    entrada.tipoPep(),
                    entrada.cargo(),
                    entrada.institucion(),
                    ahora);
            declaraciones.reemplazarBeneficiarios(dsl, entrada.usuarioId(), entrada.beneficiariosFinales());

            // R-UIF-11: cerrar antes de abrir. El EXCLUDE sobre tstzrange no tolera
            // dos vigentes ni por un instante dentro de la misma transaccion.
            calificaciones.cerrarVigente(dsl, entrada.usuarioId(), ahora);
            calificaciones.calificar(
                    dsl,
                    entrada.usuarioId(),
                    resultado.nivel().name(),
                    resultado.exigeDiligenciaReforzada() ? DILIGENCIA_REFORZADA : "SIMPLIFICADA",
                    resultado.exigeDiligenciaReforzada() ? mesesRevisionRiesgoAlto : mesesRevisionRiesgoAlto * 2,
                    BigDecimal.ZERO,
                    Optional.empty(),
                    "Declaracion PEP",
                    ahora);

            if (resultado.exigeDiligenciaReforzada()) {
                // El trigger tg_ddd_pep exige REFORZADA cuando hay PEP vigente: se
                // eleva la existente, o se abre una si la persona no tenia ninguna.
                if (diligencias.vigenteDe(dsl, entrada.usuarioId()).isPresent()) {
                    diligencias.elevarTipo(dsl, entrada.usuarioId(), DILIGENCIA_REFORZADA);
                } else {
                    diligencias.abrir(
                            dsl,
                            entrada.usuarioId(),
                            DILIGENCIA_REFORZADA,
                            "EN_PROCESO",
                            SIN_DOCUMENTOS,
                            SIN_DOCUMENTOS,
                            ahora);
                }
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.pep_declarado",
                            "declaracion_pep",
                            declaracionId,
                            Map.of(
                                    "usuarioId", entrada.usuarioId().toString(),
                                    "esPep", Boolean.toString(entrada.esPep()),
                                    "nivelRiesgo", resultado.nivel().name()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDeclaracion(
                    declaracionId,
                    resultado.exigeDiligenciaReforzada(),
                    resultado.nivel().name());
        });
    }

    /**
     * Lo que hace cumplimiento cuando auditoria confirma una coincidencia de lista.
     *
     * <p>Llega por evento y no por consulta: {@code coincidencia_lista} vive en el
     * esquema de auditoria, y cumplimiento no lee esquemas ajenos (invariante 11).
     */
    @Transactional
    public Optional<UUID> alConfirmarseCoincidencia(
            UUID usuarioId, UUID eventoId, String nombreCoincidente, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // Un segundo aviso sobre la misma persona no abre un segundo expediente:
            // duplicar casos dispersa la investigacion en vez de reforzarla.
            if (casos.hayCasoAbiertoDe(dsl, usuarioId)) {
                return Optional.<UUID>empty();
            }
            UUID casoId = casos.abrir(
                    dsl,
                    usuarioId,
                    ctx.usuarioId(),
                    "ALERTA",
                    "ALTA",
                    "Coincidencia de lista confirmada para " + nombreCoincidente,
                    ahora,
                    ahora.plusDays(diasPlazoCasoLft));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.caso_lft_abierto",
                            "caso_investigacion_lft",
                            casoId,
                            Map.of("usuarioId", usuarioId.toString(), "origen", "ALERTA"),
                            UUID.fromString(ctx.traza().id())));
            return Optional.of(casoId);
        });
    }

    public record EntradaDeclaracion(
            UUID usuarioId,
            boolean esPep,
            Optional<String> tipoPep,
            Optional<String> cargo,
            Optional<String> institucion,
            List<BeneficiarioFinal> beneficiariosFinales) {

        public static EntradaDeclaracion noPep(UUID usuarioId) {
            return new EntradaDeclaracion(
                    usuarioId, false, Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        }
    }

    public record SalidaDeclaracion(UUID declaracionId, boolean exigeDiligenciaReforzada, String nivelRiesgo) {}
}
