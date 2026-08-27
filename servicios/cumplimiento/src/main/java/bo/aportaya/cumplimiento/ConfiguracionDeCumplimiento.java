package bo.aportaya.cumplimiento;

import bo.aportaya.cumplimiento.dominio.PlazosDelIncidente;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lo que este servicio inyecta: reloj, outbox y la politica de plazos de incidente. */
@Configuration
public class ConfiguracionDeCumplimiento {

    @Bean
    public Reloj reloj() {
        return Reloj.delSistema();
    }

    @Bean
    public Outbox outbox(@Value("${aportaya.esquema}") String esquema) {
        return new Outbox(esquema);
    }

    /**
     * Los plazos de reporte de incidente, por severidad.
     *
     * <p>Van en configuracion y no cableados porque una circular puede cambiarlos, y no
     * en el catalogo de la boveda porque el modelo no tiene tabla para ellos — es un
     * hueco declarado, no un descuido. Mientras tanto viven en un solo lugar, visible y
     * auditable, en vez de repartidos por el codigo.
     *
     * <p>Los valores por omision son deliberadamente <b>cortos</b>: si la politica real
     * es mas laxa, se declara; si nadie la declara, el sistema exige mas de lo que la
     * norma pide en vez de menos.
     */
    @Bean
    public PlazosDelIncidente plazosDelIncidente(
            @Value("${cumplimiento.incidentes.horas-reporte-critica:24}") long critica,
            @Value("${cumplimiento.incidentes.horas-reporte-alta:48}") long alta,
            @Value("${cumplimiento.incidentes.horas-reporte-media:72}") long media,
            @Value("${cumplimiento.incidentes.horas-reporte-baja:120}") long baja,
            @Value("${cumplimiento.incidentes.horas-notificar-titulares:72}") long titulares) {
        return new PlazosDelIncidente(
                Map.of(
                        "CRITICA", Duration.ofHours(critica),
                        "ALTA", Duration.ofHours(alta),
                        "MEDIA", Duration.ofHours(media),
                        "BAJA", Duration.ofHours(baja)),
                Duration.ofHours(titulares));
    }
}
