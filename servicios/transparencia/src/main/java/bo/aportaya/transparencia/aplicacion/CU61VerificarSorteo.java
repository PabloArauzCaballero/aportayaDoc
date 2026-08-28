package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.SorteoVerificable;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.transparencia.infraestructura.CadenaRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-61 · Verificar publicamente el sorteo.
 *
 * <p>Que «el sorteo fue limpio» deje de ser una afirmacion nuestra y pase a ser algo
 * que **cualquiera comprueba sin pedirnos permiso**. Por eso la ruta es publica y por
 * eso el atomo que verifica es {@link SorteoVerificable}, **el mismo que uso CU-60
 * para sortear**: si la verificacion usara otra implementacion estariamos comprobando
 * que dos codigos nuestros coinciden entre si, no que el sorteo es correcto.
 *
 * <p>El paquete del sorteo vive en el esquema de {@code grupos} y **no lo leemos**
 * (invariante 11): llega como entrada, resuelto por el cliente de ese servicio fuera
 * de esta transaccion (invariante 6). Lo que aporta transparencia es el veredicto y
 * el registro de que alguien pregunto.
 */
@Service
public class CU61VerificarSorteo {

    private final Datos datos;
    private final CadenaRepositorio cadenas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU61VerificarSorteo(Datos datos, CadenaRepositorio cadenas, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.cadenas = cadenas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaSorteo verificar(PaqueteDeSorteo paquete, ContextoSesion ctx) {
        if (paquete == null) {
            throw new ErrorDeNegocio(CodigoError.de(61, 2), "Ese sorteo no existe.");
        }
        // AP-CU61-01: antes del revelado no hay nada que verificar, y ese es el punto.
        // Devolver el hash comprometido no es una concesion: es la prueba de que el
        // resultado ya estaba fijado antes de conocerse.
        if (paquete.semillaRevelada() == null || paquete.semillaRevelada().isBlank()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(61, 1),
                    "El sorteo sigue comprometido; el hash publicado es " + paquete.hashComprometido() + ".");
        }

        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        boolean compromisoCoincide = SorteoVerificable.verificarCompromiso(
                paquete.semillaRevelada(), paquete.entropias(), paquete.hashComprometido());
        String hashRecomputado = SorteoVerificable.hashDelCompromiso(paquete.semillaRevelada(), paquete.entropias());

        List<Integer> ordenRecomputado =
                SorteoVerificable.barajarDeterminista(paquete.semillaRevelada(), paquete.cuposEnOrdenOriginal());
        Integer primerDiscrepante = primerDiscrepante(ordenRecomputado, paquete.ordenPublicado());
        boolean ordenCoincide = primerDiscrepante == null;
        boolean verifica = compromisoCoincide && ordenCoincide;

        return datos.conContexto(ctx, dsl -> {
            // Se registra tambien cuando falla. Una verificacion negativa que no deja
            // rastro es una verificacion que no ocurrio.
            cadenas.registrarVerificacion(
                    dsl,
                    // `codigo` es VARCHAR(40): «SOR-» + UUID entra justo, «SORTEO-» no.
                    "SOR-" + paquete.sorteoId(),
                    "ESTADO_GRUPO",
                    paquete.sorteoId(),
                    paquete.hashComprometido(),
                    hashRecomputado,
                    verifica ? "COINCIDE" : "NO_COINCIDE",
                    ahora);

            if (verifica) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "transparencia.sorteo_verificado",
                                "sorteo_turnos",
                                paquete.sorteoId(),
                                Map.of("resultado", "COINCIDE"),
                                UUID.fromString(ctx.traza().id())));
            } else {
                // Si la verificacion publica falla, el problema es nuestro, no del
                // verificador. El incidente vive en el esquema de auditoria: lo pide el
                // evento, no lo escribimos nosotros (invariante 11).
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "transparencia.sorteo_verificacion_fallida",
                                "sorteo_turnos",
                                paquete.sorteoId(),
                                Map.of(
                                        "hashEsperado",
                                        paquete.hashComprometido(),
                                        "hashRecomputado",
                                        hashRecomputado,
                                        "compromisoCoincide",
                                        Boolean.toString(compromisoCoincide),
                                        "primerCupoDiscrepante",
                                        primerDiscrepante == null ? "" : primerDiscrepante.toString(),
                                        "severidad",
                                        "ALTA"),
                                UUID.fromString(ctx.traza().id())));
            }

            return new SalidaSorteo(
                    verifica,
                    paquete.hashComprometido(),
                    hashRecomputado,
                    ordenCoincide,
                    primerDiscrepante,
                    paquete.semillaRevelada(),
                    paquete.entropias(),
                    paquete.metodo(),
                    ordenRecomputado);
        });
    }

    /** El primer cupo que difiere, que es el unico que sirve para explicar el fallo. */
    private Integer primerDiscrepante(List<Integer> recomputado, List<Integer> publicado) {
        int comunes = Math.min(recomputado.size(), publicado.size());
        for (int i = 0; i < comunes; i++) {
            if (!recomputado.get(i).equals(publicado.get(i))) {
                return recomputado.get(i);
            }
        }
        if (recomputado.size() != publicado.size()) {
            return comunes < recomputado.size() ? recomputado.get(comunes) : publicado.get(comunes);
        }
        return null;
    }

    /** Lo que publica {@code grupos}. Llega resuelto: aca no se lee su esquema. */
    public record PaqueteDeSorteo(
            UUID sorteoId,
            String hashComprometido,
            String semillaRevelada,
            List<String> entropias,
            String metodo,
            List<Integer> cuposEnOrdenOriginal,
            List<Integer> ordenPublicado) {}

    public record SalidaSorteo(
            boolean verifica,
            String hashEsperado,
            String hashRecomputado,
            boolean ordenCoincide,
            Integer primerCupoDiscrepante,
            String semilla,
            List<String> entropias,
            String metodo,
            List<Integer> cupos) {}
}
