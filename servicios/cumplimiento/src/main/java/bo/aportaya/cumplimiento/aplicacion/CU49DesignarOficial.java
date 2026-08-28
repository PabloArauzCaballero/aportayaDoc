package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.CoberturaDeCapacitacion;
import bo.aportaya.cumplimiento.infraestructura.GobiernoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-49 · Designar al oficial de cumplimiento y capacitar.
 *
 * <p>**No puede existir un solo dia sin oficial activo.** Por eso la baja del titular y
 * la promocion del suplente ocurren en la misma transaccion: entre las dos operaciones
 * no hay un instante en que la entidad no tenga a quien responda ante el regulador.
 *
 * <p>Y **el designado no puede tener funciones operativas incompatibles** (R-SEG-04):
 * quien opera no puede ser quien controla que se opere bien. Si lo fuera, el control
 * seria una formalidad que se firma a si misma.
 */
@Service
public class CU49DesignarOficial {

    private final Datos datos;
    private final GobiernoRepositorio gobierno;
    private final Outbox outbox;
    private final Reloj reloj;

    /** Cuantos dias hay para comunicar la designacion al regulador. Es normativo. */
    private final int diasParaComunicar;

    /** Cuanto tiene alguien recien ingresado antes de contar como pendiente. */
    private final int plazoDeCapacitacionDesdeElAlta;

    /** Roles operativos que hacen incompatible la designacion (R-SEG-04). */
    private final Set<String> rolesIncompatibles;

    public CU49DesignarOficial(
            Datos datos,
            GobiernoRepositorio gobierno,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.oficial.dias-para-comunicar}") int diasParaComunicar,
            @Value("${aportaya.oficial.plazo-de-capacitacion}") int plazoDeCapacitacionDesdeElAlta,
            Set<String> rolesIncompatibles) {
        this.datos = datos;
        this.gobierno = gobierno;
        this.outbox = outbox;
        this.reloj = reloj;
        this.diasParaComunicar = diasParaComunicar;
        this.plazoDeCapacitacionDesdeElAlta = plazoDeCapacitacionDesdeElAlta;
        this.rolesIncompatibles = rolesIncompatibles;
    }

    @Transactional
    public SalidaDesignacion designar(EntradaDesignacion entrada, ContextoSesion ctx) {
        // AP-CU49-03 · R-LIC-03: sin acta no hay respaldo del directorio, y una
        // designacion sin respaldo no es oponible a nadie.
        if (entrada.actaDesignacion() == null || entrada.actaDesignacion().isBlank()) {
            throw new ErrorDeNegocio(CodigoError.de(49, 3), "La designacion exige acta del directorio.");
        }
        // AP-CU49-02 · R-SEG-04.
        var choque = entrada.rolesDelDesignado().stream()
                .filter(rolesIncompatibles::contains)
                .toList();
        if (!choque.isEmpty()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(49, 2),
                    "El designado tiene funciones operativas incompatibles: " + String.join(", ", choque) + ".");
        }

        LocalDate hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();

        return datos.conContexto(ctx, dsl -> {
            if ("TITULAR".equals(entrada.tipo()) && gobierno.titularActivo(dsl).isPresent()) {
                // AP-CU49-01 · R-UIF-12. Dos titulares es no tener ninguno: ante el
                // regulador responde una sola persona.
                throw new ErrorDeNegocio(
                        CodigoError.de(49, 1), "Ya hay un titular activo: primero la baja, despues la designacion.");
            }

            UUID id = gobierno.designar(
                    dsl,
                    entrada.usuarioId(),
                    entrada.tipo(),
                    entrada.fechaDesignacion(),
                    entrada.actaDesignacion(),
                    true);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.oficial_designado",
                            "oficial_cumplimiento",
                            id,
                            Map.of(
                                    "tipo", entrada.tipo(),
                                    "acta", entrada.actaDesignacion(),
                                    "plazoComunicacionHasta",
                                            entrada.fechaDesignacion()
                                                    .plusDays(diasParaComunicar)
                                                    .toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDesignacion(id, true, entrada.fechaDesignacion().plusDays(diasParaComunicar), false);
        });
    }

    /**
     * Baja del titular y promocion del suplente, **en la misma transaccion**.
     *
     * <p>Hacerlo en dos pasos dejaria una ventana —minutos u horas— sin oficial activo.
     * Si en esa ventana llega un requerimiento, no hay quien responda y el incumplimiento
     * es real aunque haya durado poco.
     */
    @Transactional
    public SalidaDesignacion darDeBajaAlTitular(EntradaBaja entrada, ContextoSesion ctx) {
        LocalDate hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();

        return datos.conContexto(ctx, dsl -> {
            var titular = gobierno.titularActivo(dsl)
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(49, 1), "No hay titular activo que dar de baja."));
            var suplente = gobierno.suplenteActivo(dsl);
            // AP-CU49-04.
            if (suplente.isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(49, 4), "No se da de baja al titular sin un suplente que asuma.");
            }

            gobierno.darDeBaja(dsl, titular.id(), entrada.fechaBaja());
            gobierno.promoverASuplente(dsl, suplente.get().id(), entrada.fechaBaja(), entrada.actaDeRelevo());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.oficial_relevado",
                            "oficial_cumplimiento",
                            suplente.get().id(),
                            Map.of(
                                    "salienteId", titular.id().toString(),
                                    "fechaBaja", entrada.fechaBaja().toString(),
                                    "acta", entrada.actaDeRelevo()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDesignacion(
                    suplente.get().id(), true, entrada.fechaBaja().plusDays(diasParaComunicar), true);
        });
    }

    @Transactional
    public UUID capacitar(EntradaCapacitacion entrada, ContextoSesion ctx) {
        return datos.conContexto(
                ctx,
                dsl -> gobierno.registrarCapacitacion(
                        dsl,
                        entrada.usuarioId(),
                        entrada.tema(),
                        entrada.modalidad(),
                        entrada.horas(),
                        entrada.fecha(),
                        entrada.calificacion(),
                        entrada.aprobada(),
                        entrada.evidenciaUrl(),
                        entrada.periodo()));
    }

    /** La cobertura del periodo, con los pendientes por nombre. */
    @Transactional
    public CoberturaDeCapacitacion.Cobertura cobertura(
            String periodo, List<CoberturaDeCapacitacion.Empleado> personal, ContextoSesion ctx) {
        LocalDate hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();
        return datos.conContexto(
                ctx,
                dsl -> CoberturaDeCapacitacion.calcular(
                        periodo,
                        personal,
                        gobierno.aprobaronEnElPeriodo(dsl, periodo),
                        hoy,
                        plazoDeCapacitacionDesdeElAlta));
    }

    public record EntradaDesignacion(
            UUID usuarioId,
            String tipo,
            LocalDate fechaDesignacion,
            String actaDesignacion,
            Set<String> rolesDelDesignado) {}

    public record EntradaBaja(LocalDate fechaBaja, String actaDeRelevo) {}

    public record EntradaCapacitacion(
            UUID usuarioId,
            String tema,
            String modalidad,
            BigDecimal horas,
            LocalDate fecha,
            BigDecimal calificacion,
            boolean aprobada,
            String evidenciaUrl,
            String periodo) {}

    public record SalidaDesignacion(
            UUID oficialId, boolean activo, LocalDate plazoComunicacionHasta, boolean suplentePromovido) {}
}
