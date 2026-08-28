package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.transparencia.dominio.CriterioDeInsignia;
import bo.aportaya.transparencia.infraestructura.InsigniaRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-74 · Otorgar y revocar una insignia.
 *
 * <p>**No hay endpoint de otorgamiento manual.** Las insignias se ganan contra los
 * datos o no se ganan; una insignia que alguien puede conceder a dedo no dice nada de
 * quien la lleva y deja a la plataforma decidiendo simpatias.
 *
 * <p>**Revocar no borra** (R-REP-05). Queda la fila, la fecha y el motivo. Borrarla
 * dejaria a la persona sin poder saber por que perdio algo que tenia, y a nosotros sin
 * poder explicarlo.
 *
 * <p>El reintento es inocuo: si ya la tiene, no se crea una segunda fila
 * ({@code uq_insignia_usuario}).
 */
@Service
public class CU74EvaluarInsignias {

    private final Datos datos;
    private final InsigniaRepositorio insignias;
    private final Outbox outbox;
    private final Reloj reloj;

    /**
     * El desempeño minimo que el criterio publicado de ORGANIZADOR_CONFIABLE exige.
     * Llega como dato: mover la vara de un logro publicado no puede exigir un
     * despliegue.
     */
    private final java.math.BigDecimal desempenoMinimoDeOrganizador;

    public CU74EvaluarInsignias(
            Datos datos,
            InsigniaRepositorio insignias,
            Outbox outbox,
            Reloj reloj,
            java.math.BigDecimal desempenoMinimoDeOrganizador) {
        this.datos = datos;
        this.insignias = insignias;
        this.outbox = outbox;
        this.reloj = reloj;
        this.desempenoMinimoDeOrganizador = desempenoMinimoDeOrganizador;
    }

    /** Evalua las insignias que el hecho pudo desbloquear, y otorga las que corresponden. */
    @Transactional
    public List<Otorgamiento> evaluar(EntradaEvaluacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        List<String> candidatas = entrada.tipoDeEvento() == null
                ? entrada.codigosAEvaluar()
                : CriterioDeInsignia.afectadasPor(entrada.tipoDeEvento());

        return datos.conContexto(ctx, dsl -> {
            var otorgamientos = new ArrayList<Otorgamiento>();
            for (String codigo : candidatas) {
                var delCatalogo = insignias.insigniaPorCodigo(dsl, codigo);
                if (delCatalogo.isEmpty()) {
                    continue; // discontinuada del catalogo: no se otorga ni se explica de mas
                }
                var evaluacion = CriterioDeInsignia.evaluar(codigo, entrada.hechos(), desempenoMinimoDeOrganizador);
                var yaLaTiene = insignias.otorgadaA(
                        dsl, entrada.usuarioId(), delCatalogo.get().id());

                if (yaLaTiene.isPresent()) {
                    // El reintento es inocuo: quien ya la tiene la conserva, incluso si
                    // el criterio del catalogo cambio despues. Quitarle a alguien un
                    // logro porque movimos la vara es reescribirle el pasado.
                    otorgamientos.add(new Otorgamiento(
                            yaLaTiene.get().id(),
                            codigo,
                            yaLaTiene.get().otorgadaEn(),
                            evaluacion.motivoLegible(),
                            yaLaTiene.get().revocadaEn() != null,
                            false));
                    continue;
                }
                if (!evaluacion.cumple()) {
                    continue;
                }

                UUID otorgadaId = insignias.otorgar(
                        dsl, entrada.usuarioId(), delCatalogo.get().id(), ahora);
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "transparencia.insignia_otorgada",
                                "insignia_otorgada",
                                otorgadaId,
                                Map.of(
                                        "usuarioId", entrada.usuarioId().toString(),
                                        "insigniaCodigo", codigo,
                                        "motivoLegible", evaluacion.motivoLegible()),
                                UUID.fromString(ctx.traza().id())));
                otorgamientos.add(new Otorgamiento(otorgadaId, codigo, ahora, evaluacion.motivoLegible(), false, true));
            }
            return List.copyOf(otorgamientos);
        });
    }

    /** Revocar exige motivo: {@code ck_insignia_revocacion_motivada} tampoco lo permite sin el. */
    @Transactional
    public Otorgamiento revocar(UUID otorgadaId, String motivo, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            boolean revocada = insignias.revocar(dsl, otorgadaId, motivo, ahora);
            if (!revocada) {
                // AP-CU74-04: o no existe, o ya estaba revocada. Se responde lo mismo:
                // quien revoca ya sabe cual busca.
                throw new ErrorDeNegocio(CodigoError.de(74, 4), "Esa insignia no esta vigente para revocar.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "transparencia.insignia_revocada",
                            "insignia_otorgada",
                            otorgadaId,
                            Map.of("motivoRevocacion", motivo),
                            UUID.fromString(ctx.traza().id())));
            return new Otorgamiento(otorgadaId, null, ahora, motivo, true, false);
        });
    }

    /**
     * @param tipoDeEvento cuando llega por evento de dominio; si es {@code null} se
     *     evaluan los codigos que se pasen (reevaluacion tras un recalculo)
     */
    public record EntradaEvaluacion(
            UUID usuarioId, String tipoDeEvento, List<String> codigosAEvaluar, CriterioDeInsignia.Hechos hechos) {}

    public record Otorgamiento(
            UUID otorgadaId,
            String insigniaCodigo,
            OffsetDateTime otorgadaEn,
            String motivoLegible,
            boolean revocada,
            boolean esNueva) {}
}
