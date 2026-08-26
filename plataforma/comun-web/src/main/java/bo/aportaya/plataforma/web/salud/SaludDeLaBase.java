package bo.aportaya.plataforma.web.salud;

import javax.sql.DataSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sonda de readiness: mira la base de verdad.
 *
 * <p>{@code liveness} no toca la base a proposito. Si lo hiciera, una base lenta
 * mataria y reiniciaria las catorce replicas justo cuando la base esta peor — que es
 * la forma mas rapida de convertir una degradacion en una caida.
 */
@Component("base")
public class SaludDeLaBase implements HealthIndicator {

    private final JdbcTemplate consultas;

    public SaludDeLaBase(DataSource fuente) {
        this.consultas = new JdbcTemplate(fuente);
    }

    @Override
    public Health health() {
        try {
            consultas.queryForObject("SELECT 1", Integer.class);
            return Health.up().build();
        } catch (RuntimeException e) {
            // Sin la causa: el cuerpo de una sonda es publico dentro del clúster.
            return Health.down().withDetail("motivo", "la base no responde").build();
        }
    }
}
