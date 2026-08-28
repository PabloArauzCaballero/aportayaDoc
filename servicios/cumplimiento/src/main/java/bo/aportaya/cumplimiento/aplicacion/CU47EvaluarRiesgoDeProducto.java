package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto;
import bo.aportaya.cumplimiento.infraestructura.EvaluacionProductoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.LicenciaRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-47 · Evaluar el riesgo del producto antes de lanzarlo.
 *
 * <p>Evaluar despues del lanzamiento es escribir la autopsia. Las cuatro puertas, en
 * orden:
 *
 * <ol>
 *   <li>**El producto tiene que caber en la licencia** (R-LIC-01). Lanzar fuera del
 *       alcance autorizado no es un riesgo del producto: es operar sin permiso.
 *   <li>**Los cuatro factores son obligatorios.** Evaluar tres es no haber mirado el
 *       cuarto, y el que no se mira es siempre el que despues explota.
 *   <li>**Ningun riesgo alto sin control.** Escribir el riesgo y no que se hace con el
 *       convierte la matriz en una lista de disculpas anticipadas.
 *   <li>**Si la norma exige no objecion, no entra en vigencia sin ella** (R-LIC-04).
 * </ol>
 */
@Service
public class CU47EvaluarRiesgoDeProducto {

    private final Datos datos;
    private final EvaluacionProductoRepositorio evaluaciones;
    private final LicenciaRepositorio licencias;
    private final Outbox outbox;
    private final Reloj reloj;

    /** Donde empieza cada nivel de riesgo del producto. Politica, no constante. */
    private final RiesgoDelProducto.Escala escala;

    public CU47EvaluarRiesgoDeProducto(
            Datos datos,
            EvaluacionProductoRepositorio evaluaciones,
            LicenciaRepositorio licencias,
            Outbox outbox,
            Reloj reloj,
            RiesgoDelProducto.Escala escala) {
        this.datos = datos;
        this.evaluaciones = evaluaciones;
        this.licencias = licencias;
        this.outbox = outbox;
        this.reloj = reloj;
        this.escala = escala;
    }

    @Transactional
    public SalidaEvaluacion evaluar(EntradaEvaluacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        var resultado = RiesgoDelProducto.evaluar(entrada.riesgos(), entrada.controlesPorRiesgo(), escala);
        // AP-CU47-03 antes que nada: sin los cuatro factores no hay evaluacion que
        // discutir.
        if (!resultado.factoresFaltantes().isEmpty()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(47, 3),
                    "Faltan factores obligatorios: " + String.join(", ", resultado.factoresFaltantes()) + ".");
        }
        // AP-CU47-02.
        if (!resultado.riesgosSinControl().isEmpty()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(47, 2),
                    "Hay riesgo alto sin control asociado: " + String.join("; ", resultado.riesgosSinControl()) + ".");
        }

        return datos.conContexto(ctx, dsl -> {
            // AP-CU47-01 · R-LIC-01. La licencia se consulta antes de escribir nada.
            var licencia = licencias.vigente(dsl, ahora.toLocalDate());
            // Las tres condiciones, y las tres hacen falta: **solo OTORGADA habilita**
            // (una licencia en tramite o revocada no autoriza nada), la vigencia no
            // puede estar vencida, y el servicio tiene que estar dentro del alcance.
            boolean cubierto = licencia.isPresent()
                    && licencia.get().estado().habilitaServicioFinanciero()
                    && licencia.get().vigente()
                    && licencia.get().alcance().contains(entrada.servicioDeLicencia());
            if (!cubierto) {
                throw new ErrorDeNegocio(
                        CodigoError.de(47, 1),
                        "El servicio " + entrada.servicioDeLicencia() + " excede el alcance de la licencia vigente.");
            }

            int version = evaluaciones.ultimaVersion(dsl, entrada.producto()) + 1;
            UUID id;
            try {
                id = evaluaciones.crear(
                        dsl,
                        entrada.producto(),
                        version,
                        entrada.riesgosJson(),
                        resultado.nivelLft(),
                        entrada.controlesJson(),
                        entrada.requiereNoObjecion());
            } catch (org.jooq.exception.IntegrityConstraintViolationException
                    | org.springframework.dao.DataIntegrityViolationException e) {
                // AP-CU47-05 · uq_evaluacion_producto_version. Dos evaluaciones con la
                // misma version harian imposible decir cual rigio.
                throw new ErrorDeNegocio(
                        CodigoError.de(47, 5),
                        "Ya existe la version " + version + " del producto " + entrada.producto() + ".");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.evaluacion_producto_creada",
                            "evaluacion_riesgo_producto",
                            id,
                            Map.of(
                                    "producto", entrada.producto(),
                                    "version", Integer.toString(version),
                                    "nivelRiesgoLft", resultado.nivelLft(),
                                    "requiereNoObjecion", Boolean.toString(entrada.requiereNoObjecion()),
                                    "reglasSugeridas", String.join(",", resultado.reglasSugeridas())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEvaluacion(
                    id,
                    version,
                    resultado.nivelLft(),
                    "BORRADOR",
                    // Bloquea la habilitacion hasta que el comite la apruebe. Un producto
                    // en borrador no se lanza.
                    true,
                    resultado.reglasSugeridas());
        });
    }

    /**
     * Aprueba la evaluacion. La firma quien preside el comite con quorum, y **la no
     * objecion tiene que estar** si la norma la exige.
     */
    @Transactional
    public SalidaEvaluacion aprobar(EntradaAprobacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var evaluacion = evaluaciones
                    .porId(dsl, entrada.evaluacionId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(47, 5), "Esa evaluacion no existe."));

            // AP-CU47-06: quien aprueba tiene que venir de una sesion con quorum. El
            // quorum lo verifica CU-94 y llega resuelto.
            if (!entrada.comiteConQuorum()) {
                throw new ErrorDeNegocio(CodigoError.de(47, 6), "Quien aprueba no integra un comite con quorum.");
            }
            // AP-CU47-04 · R-LIC-04. HUECO H-6: ck_evaluacion_no_objecion vigila el
            // estado 'VIGENTE', que ck_evaluacion_riesgo_producto_estado no admite, asi
            // que la base nunca lo verifica. Lo exige este caso de uso.
            if (evaluacion.requiereNoObjecion() && !entrada.hayNoObjecion()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(47, 4),
                        "El producto exige no objecion del supervisor y todavia no la tiene (R-LIC-04).");
            }
            if (!evaluaciones.aprobar(dsl, entrada.evaluacionId(), entrada.aprobadaPor(), ahora.toLocalDate())) {
                throw new ErrorDeNegocio(CodigoError.de(47, 5), "Esa evaluacion ya no esta en borrador.");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.evaluacion_producto_aprobada",
                            "evaluacion_riesgo_producto",
                            entrada.evaluacionId(),
                            Map.of(
                                    "producto", evaluacion.producto(),
                                    "version", Integer.toString(evaluacion.version()),
                                    "actaComite", entrada.numeroDeActa()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEvaluacion(
                    entrada.evaluacionId(), evaluacion.version(), evaluacion.nivelLft(), "APROBADA", false, List.of());
        });
    }

    public record EntradaEvaluacion(
            String producto,
            String servicioDeLicencia,
            List<RiesgoDelProducto.Riesgo> riesgos,
            Map<Integer, List<String>> controlesPorRiesgo,
            String riesgosJson,
            String controlesJson,
            boolean requiereNoObjecion) {}

    public record EntradaAprobacion(
            UUID evaluacionId, UUID aprobadaPor, boolean comiteConQuorum, boolean hayNoObjecion, String numeroDeActa) {}

    public record SalidaEvaluacion(
            UUID evaluacionId,
            int version,
            String nivelRiesgoLft,
            String estado,
            boolean bloqueaHabilitacion,
            List<String> reglasSugeridas) {}
}
