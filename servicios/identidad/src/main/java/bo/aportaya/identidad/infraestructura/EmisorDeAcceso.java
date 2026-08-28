package bo.aportaya.identidad.infraestructura;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * El unico emisor de tokens del sistema (ADR-024).
 *
 * <p>RS256 y no HS256: con clave simetrica los catorce servicios tendrian que conocer
 * el secreto de firma, y entonces cualquiera de ellos podria emitir un token de
 * administrador. Con clave asimetrica **solo identidad firma** y los otros trece solo
 * verifican.
 *
 * <p>Los reclamos son los que fija el ADR: {@code sub}, {@code rol}, {@code permisos},
 * {@code nivel_diligencia}, {@code dispositivo}, {@code exp} y {@code jti}. Ni uno mas:
 * un token es un dato que viaja por catorce procesos y queda en logs y trazas, asi que
 * lo que no hace falta para autorizar no entra.
 *
 * <p><b>La clave.</b> Viene por configuracion en formato JWK. Si no viene, se genera una
 * al arrancar y **queda solo en memoria**: sirve para desarrollo y para las pruebas de
 * punta a punta, y tiene el precio de que al reiniciar identidad los tokens vivos dejan
 * de validar. En cualquier entorno con datos reales se configura, porque una clave que
 * cambia sola no es una clave.
 */
@Component
public class EmisorDeAcceso {

    /** ADR-024: vida corta, porque un token firmado no se puede revocar. */
    private static final Duration VIGENCIA = Duration.ofMinutes(15);

    private static final int TAMANO_DE_CLAVE = 2048;

    private final RSAKey clave;

    public EmisorDeAcceso(@Value("${aportaya.jwt.clave-firma:}") String claveJwk) {
        this.clave = claveJwk == null || claveJwk.isBlank() ? generar() : leer(claveJwk);
    }

    /**
     * El token de acceso de una sesion ya abierta.
     *
     * @param permisos los efectivos, que CU-08 calculo al asignar el rol. Vacio
     *     significa <b>no puede nada</b>: el token no es una llave maestra.
     */
    public Emitido emitir(
            UUID usuarioId, String rol, List<String> permisos, String nivelDiligencia, String dispositivo) {

        Instant ahora = Instant.now();
        Instant vence = ahora.plus(VIGENCIA);

        var reclamos = new JWTClaimsSet.Builder()
                .subject(usuarioId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(vence))
                .claim("rol", rol)
                .claim("permisos", permisos)
                .claim("nivel_diligencia", nivelDiligencia)
                .claim("dispositivo", dispositivo)
                .build();

        try {
            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(clave.getKeyID())
                            .build(),
                    reclamos);
            jwt.sign(new RSASSASigner(clave));
            return new Emitido(jwt.serialize(), vence);
        } catch (JOSEException imposible) {
            throw new IllegalStateException("No se pudo firmar el token de acceso", imposible);
        }
    }

    /** Lo que se publica: la parte publica, y nada mas. */
    public Map<String, Object> jwks() {
        return new JWKSet(clave.toPublicJWK()).toJSONObject();
    }

    private static RSAKey generar() {
        try {
            return new RSAKeyGenerator(TAMANO_DE_CLAVE)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } catch (JOSEException imposible) {
            throw new IllegalStateException("No se pudo generar la clave de firma", imposible);
        }
    }

    private static RSAKey leer(String claveJwk) {
        try {
            return RSAKey.parse(claveJwk);
        } catch (java.text.ParseException mal) {
            throw new IllegalStateException("aportaya.jwt.clave-firma no es un JWK RSA valido", mal);
        }
    }

    public record Emitido(String token, Instant expiraEn) {}
}
