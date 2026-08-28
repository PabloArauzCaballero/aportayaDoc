package bo.aportaya.identidad.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El enlace de un solo uso con el que se invita a alguien a un grupo.
 *
 * <p>{@code token_verificacion} es de este servicio, y su politica —vigencia, canales
 * permitidos, cuantas veces se puede reenviar— esta sembrada en {@code politica_token}
 * bajo el proposito {@code INVITACION_GRUPO}. {@code grupos} no puede escribir esa
 * tabla (invariante 11): pide el token y guarda su identificador.
 *
 * <p><b>Se guarda el hash, no el token.</b> Quien tenga la base no puede reconstruir
 * los enlaces vivos; el valor en claro se devuelve una sola vez, a quien lo va a enviar.
 */
@Service
public class EmitirTokenDeInvitacion {

    private static final String PROPOSITO = "INVITACION_GRUPO";

    /** Treinta y dos bytes de azar: un enlace adivinable es una invitacion para cualquiera. */
    private static final int BYTES_DE_AZAR = 32;

    private final Datos datos;
    private final Reloj reloj;
    private final Ids ids;
    private final SecureRandom azar = new SecureRandom();

    public EmitirTokenDeInvitacion(Datos datos, Reloj reloj, Ids ids) {
        this.datos = datos;
        this.reloj = reloj;
        this.ids = ids;
    }

    @Transactional
    public Emitido ejecutar(String canal, String destinoEnmascarado, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        byte[] bytes = new byte[BYTES_DE_AZAR];
        azar.nextBytes(bytes);
        String enClaro = HexFormat.of().formatHex(bytes);
        UUID id = ids.nuevo();

        return datos.conContexto(ctx, dsl -> {
            var politica = dsl.fetchOne(
                    "SELECT id, ttl_segundos FROM identidad.politica_token WHERE proposito = ?", PROPOSITO);
            if (politica == null) {
                // Denegar por omision: sin politica sembrada no se inventa una vigencia.
                throw new ErrorDeNegocio(CodigoError.de(9, 1), "No hay politica de token para invitaciones de grupo.");
            }
            dsl.execute(
                    """
                    INSERT INTO identidad.token_verificacion
                        (id, politica_id, tipo_token, proposito, hash_token, algoritmo_hash,
                         canal_entrega, destino_enmascarado, estado, emitido_en, expira_en,
                         intentos_fallidos, max_intentos, reenvios, uso_unico, clicks)
                    VALUES (?, ?, 'ENLACE', ?, encode(digest(?, 'sha256'), 'hex'), 'SHA-256',
                            ?, ?, 'EMITIDO', ?, ?, 0, 1, 0, true, 0)
                    """,
                    id,
                    politica.get("id", UUID.class),
                    PROPOSITO,
                    enClaro,
                    canal,
                    destinoEnmascarado,
                    ahora,
                    ahora.plusSeconds(politica.get("ttl_segundos", Integer.class)));
            return new Emitido(id, enClaro, ahora.plusSeconds(politica.get("ttl_segundos", Integer.class)));
        });
    }

    /** El token en claro sale UNA vez: de la base solo se puede recuperar su hash. */
    public record Emitido(UUID tokenId, String token, OffsetDateTime expiraEn) {}
}
