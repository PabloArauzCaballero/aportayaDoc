package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.transparencia.dominio.CadenaDeBloques;
import bo.aportaya.transparencia.dominio.ContenidoCanonico;
import bo.aportaya.transparencia.infraestructura.CadenaRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-73 · Verificar la cadena de transparencia.
 *
 * <p>Que auditar el grupo no dependa de que nosotros abramos la base. Con los bloques
 * publicados, cualquiera recorre la cadena y detecta **si algo fue alterado y desde
 * que bloque**.
 *
 * <p>Se usa **el mismo atomo que sella** ({@link CadenaDeBloques}). Una segunda
 * implementacion «de verificacion» comprobaria que dos codigos nuestros coinciden.
 *
 * <p>Un grupo sin bloques **no es un error de integridad**, y asi se informa. Confundir
 * «todavia no hay historia» con «la historia esta rota» asusta a quien no hizo nada.
 */
@Service
public class CU73VerificarCadena {

    private final Datos datos;
    private final CadenaRepositorio cadenas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU73VerificarCadena(Datos datos, CadenaRepositorio cadenas, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.cadenas = cadenas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaCadena verificar(UUID grupoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            List<CadenaRepositorio.Persistido> persistidos = cadenas.cadenaDe(dsl, grupoId);
            if (persistidos.isEmpty()) {
                // AP-CU73-02: no hay historia sellada todavia. No se registra
                // verificacion porque no hay nada que verificar.
                throw new ErrorDeNegocio(
                        CodigoError.de(73, 2), "Ese grupo todavia no sello historia; no hay nada que verificar.");
            }

            // La raiz se recomputa desde los hechos sellados: si alguien reescribio un
            // resumen despues del sellado, la raiz de hoy ya no es la que se guardo.
            var hojas = cadenas.hojasDelGrupo(dsl, grupoId);
            List<CadenaDeBloques.Bloque> bloques = persistidos.stream()
                    .map(b -> new CadenaDeBloques.Bloque(
                            b.numero(),
                            b.hashAnterior(),
                            b.raizMerkle(),
                            b.hash(),
                            ContenidoCanonico.instante(b.desde()),
                            ContenidoCanonico.instante(b.hasta()),
                            CadenaDeBloques.raizMerkle(hojas.getOrDefault(b.id(), List.of()))))
                    .toList();

            List<CadenaDeBloques.Rotura> roturas = CadenaDeBloques.verificar(bloques);
            boolean integra = roturas.isEmpty();
            var primera = integra ? null : roturas.get(0);
            var ultimo = persistidos.get(persistidos.size() - 1);

            cadenas.registrarVerificacion(
                    dsl,
                    // `codigo` es VARCHAR(40): «CAD-» + UUID entra justo.
                    "CAD-" + grupoId,
                    "ESTADO_GRUPO",
                    grupoId,
                    ultimo.hash(),
                    // Cuando la cadena esta rota, lo que se guarda como recomputado es
                    // el hash que la cadena deberia tener hoy segun sus propios datos.
                    integra
                            ? ultimo.hash()
                            : CadenaDeBloques.hashDelBloque(
                                    ultimo.numero(),
                                    ultimo.hashAnterior(),
                                    ultimo.raizMerkle(),
                                    ContenidoCanonico.instante(ultimo.desde()),
                                    ContenidoCanonico.instante(ultimo.hasta())),
                    integra ? "COINCIDE" : "NO_COINCIDE",
                    ahora);

            if (integra) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "transparencia.cadena_verificada",
                                "bloque_transparencia",
                                grupoId,
                                Map.of("bloquesVerificados", Integer.toString(bloques.size())),
                                UUID.fromString(ctx.traza().id())));
            } else {
                // R-RIS-01: una cadena rota es un evento de riesgo operativo, no una
                // curiosidad. El incidente y el evento de riesgo viven en otros
                // esquemas; se piden por evento, con su categoria y su factor, que la
                // taxonomia cerrada exige.
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "transparencia.cadena_rota",
                                "bloque_transparencia",
                                grupoId,
                                Map.of(
                                        "primerBloqueFallido", Long.toString(primera.numeroBloque()),
                                        "componenteFallido", primera.componente(),
                                        "severidad", "ALTA",
                                        "categoriaEvento", "FALLAS_SISTEMAS",
                                        "factorRiesgo", "TECNOLOGIA_INFORMACION",
                                        "congelarSellados", "true"),
                                UUID.fromString(ctx.traza().id())));
            }

            return new SalidaCadena(
                    integra,
                    bloques.size(),
                    integra ? null : primera.numeroBloque(),
                    integra ? null : primera.componente(),
                    ultimo.selladoEn());
        });
    }

    public record SalidaCadena(
            boolean integra,
            int bloquesVerificados,
            Long primerBloqueFallido,
            String componenteFallido,
            OffsetDateTime ultimoSellado) {}
}
