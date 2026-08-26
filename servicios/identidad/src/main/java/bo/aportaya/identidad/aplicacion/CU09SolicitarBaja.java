package bo.aportaya.identidad.aplicacion;

import bo.aportaya.identidad.infraestructura.CredencialRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
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
 * CU-09 · Solicitar la baja.
 *
 * <p>La baja **no borra datos**: escribe la solicitud y, si hay obligaciones abiertas,
 * la deja marcada y enumera cuales. Rechazar sin decir que lo impide obliga al usuario
 * a adivinar, y adivinar es lo que hace que la gente crea que se le esconde algo.
 *
 * <p>Las obligaciones vienen de AFUERA: grupos activos son de `grupos`, deuda y
 * retenciones de `nucleo-financiero`. Este servicio no lee esos esquemas —invariante
 * 11— y por eso las recibe ya resueltas.
 */
@Service
public class CU09SolicitarBaja {

    private final Datos datos;
    private final CredencialRepositorio credenciales;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU09SolicitarBaja(Datos datos, CredencialRepositorio credenciales, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.credenciales = credenciales;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaBaja ejecutar(String motivo, List<ObligacionAbierta> obligaciones, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        boolean bloqueada = !obligaciones.isEmpty();

        return datos.conContexto(ctx, dsl -> {
            UUID solicitud = credenciales.solicitarBaja(dsl, ctx.usuarioId(), motivo, bloqueada, ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "identidad.baja_solicitada",
                            "solicitud_baja",
                            solicitud,
                            Map.of("usuarioId", ctx.usuarioId().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaBaja(solicitud, bloqueada, List.copyOf(obligaciones));
        });
    }

    /** Lo que impide la baja, con nombre. */
    public record ObligacionAbierta(String tipo, UUID referenciaId, String detalle) {}

    public record SalidaBaja(
            UUID solicitudBajaId, boolean bloqueadaPorObligaciones, List<ObligacionAbierta> obligacionesAbiertas) {}
}
