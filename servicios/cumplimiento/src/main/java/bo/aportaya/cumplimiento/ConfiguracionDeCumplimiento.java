package bo.aportaya.cumplimiento;

import bo.aportaya.cumplimiento.dominio.DesvioDePerfil;
import bo.aportaya.cumplimiento.dominio.NivelDeDiligencia;
import bo.aportaya.cumplimiento.dominio.PeriodicidadDeRevision;
import bo.aportaya.cumplimiento.dominio.PlazosDelIncidente;
import bo.aportaya.cumplimiento.dominio.RequisitosDeNivel;
import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La politica de cumplimiento, declarada.
 *
 * <p>Todo lo que hay aca son **umbrales y plazos normativos**, y por eso no viven dentro
 * de los casos de uso: un plazo horneado en el codigo es un plazo que nadie puede
 * cambiar cuando cambia la norma, y el invariante 10 existe para eso. Que esten en un
 * bean los deja a la vista, con su fuente escrita al lado.
 *
 * <p>Los que son un simple numero van en {@code application.yml}; los que son una
 * estructura —una tabla de plazos por severidad, los documentos que pide cada nivel—
 * van aca, porque un mapa anidado en YAML es mas facil de romper que de leer.
 */
@Configuration
public class ConfiguracionDeCumplimiento {

    /** Los documentos que exige cada nivel de diligencia. */
    @Bean
    public RequisitosDeNivel requisitosDeNivel() {
        return new RequisitosDeNivel(Map.of(
                NivelDeDiligencia.ESTANDAR,
                List.of("CEDULA", "DOMICILIO"),
                NivelDeDiligencia.AMPLIADA,
                List.of("CEDULA", "DOMICILIO", "INGRESOS"),
                NivelDeDiligencia.REFORZADA,
                List.of("CEDULA", "DOMICILIO", "INGRESOS", "ORIGEN_FONDOS")));
    }

    /** Cada cuantos meses se revisa el conocimiento del cliente, por nivel de riesgo. */
    @Bean
    public PeriodicidadDeRevision periodicidadDeRevision() {
        return new PeriodicidadDeRevision(6, 12, 24);
    }

    /**
     * Cuanto se aparta un movimiento del perfil declarado antes de llamar la atencion.
     *
     * <p>Son porcentajes de desvio, en escalera: a partir de 100 % se observa, de 200 %
     * se investiga y de 500 % se trata como caso.
     */
    @Bean
    public DesvioDePerfil.Umbrales umbralesDeDesvio(
            @Value("${aportaya.desvio.observar}") String observar,
            @Value("${aportaya.desvio.investigar}") String investigar,
            @Value("${aportaya.desvio.caso}") String caso) {
        return new DesvioDePerfil.Umbrales(new BigDecimal(observar), new BigDecimal(investigar), new BigDecimal(caso));
    }

    /** R-UIF-08: el plazo de investigacion de un caso sale de su severidad. */
    @Bean
    public Map<String, Duration> plazoPorSeveridad() {
        return Map.of(
                "CRITICA", Duration.ofDays(5),
                "ALTA", Duration.ofDays(15),
                "MEDIA", Duration.ofDays(30),
                "BAJA", Duration.ofDays(45));
    }

    /** Cuanto hay para reportar un incidente de seguridad, y para avisar a los titulares. */
    @Bean
    public PlazosDelIncidente plazosDelIncidente() {
        return new PlazosDelIncidente(
                Map.of(
                        "CRITICA", Duration.ofHours(24),
                        "ALTA", Duration.ofHours(48),
                        "MEDIA", Duration.ofHours(72),
                        "BAJA", Duration.ofHours(120)),
                Duration.ofHours(72));
    }

    /** Cuantas observaciones abiertas hacen que un producto sea de riesgo alto o medio. */
    @Bean
    public RiesgoDelProducto.Escala escalaDeRiesgoDeProducto() {
        return new RiesgoDelProducto.Escala(15, 8);
    }

    /**
     * Los roles que no puede tener quien es oficial de cumplimiento.
     *
     * <p>Quien controla no ejecuta: si el oficial tambien opera tesoreria, el control
     * interno lo firma la misma persona que lo tendria que incomodar.
     */
    @Bean
    public Set<String> rolesIncompatibles() {
        return Set.of("TESORERIA", "OPERACIONES", "COMERCIAL");
    }
}
