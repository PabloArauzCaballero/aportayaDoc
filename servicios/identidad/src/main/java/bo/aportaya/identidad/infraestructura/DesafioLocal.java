package bo.aportaya.identidad.infraestructura;

import bo.aportaya.identidad.dominio.puertos.DesafioDeFactor;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptador local del segundo factor: el que corre por omision.
 *
 * <p>ADR-033 pide adaptador local primero, y aca eso significa que el codigo NO sale
 * del proceso. Se registra que se emitio —nunca el valor— y el flujo de desarrollo
 * queda completo sin encender ningun canal.
 *
 * <p>El azar es criptografico: un codigo de seis digitos generado con
 * {@code Math.random} se adivina, y adivinarlo es entrar.
 */
@Component
public class DesafioLocal implements DesafioDeFactor {

    private static final Logger BITACORA = LoggerFactory.getLogger(DesafioLocal.class);
    private static final Duration VIGENCIA = Duration.ofMinutes(5);
    private static final int DIGITOS = 6;

    private final SecureRandom azar = new SecureRandom();
    private final Map<UUID, Desafio> vigentes = new ConcurrentHashMap<>();

    @Override
    public UUID emitir(UUID usuarioId, String tipoDeFactor) {
        UUID id = UUID.randomUUID();
        String codigo = "%06d".formatted(azar.nextInt(1_000_000));
        vigentes.put(usuarioId, new Desafio(codigo, Instant.now().plus(VIGENCIA)));
        // El valor NUNCA se registra. Un codigo en el log es un codigo filtrado.
        BITACORA.info("desafio {} emitido para el usuario {} ({} digitos)", id, usuarioId, DIGITOS);
        return id;
    }

    @Override
    public boolean validar(UUID usuarioId, String tipoDeFactor, String valorPresentado) {
        Desafio desafio = vigentes.get(usuarioId);
        if (desafio == null || Instant.now().isAfter(desafio.expiraEn())) {
            return false;
        }
        // Un solo uso: aceptado o no, el desafio se consume. Reintentar sobre el
        // mismo codigo es exactamente lo que la fuerza bruta necesita.
        vigentes.remove(usuarioId);
        return java.security.MessageDigest.isEqual(
                desafio.codigo().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                valorPresentado.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record Desafio(String codigo, Instant expiraEn) {}
}
