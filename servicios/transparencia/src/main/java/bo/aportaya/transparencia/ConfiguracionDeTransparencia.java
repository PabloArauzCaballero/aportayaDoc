package bo.aportaya.transparencia;

import bo.aportaya.transparencia.dominio.ModeracionDeResena;
import bo.aportaya.transparencia.dominio.PuntajeDeReputacion;
import bo.aportaya.transparencia.dominio.SenalDeRiesgo;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Las escalas con las que este servicio juzga.
 *
 * <p>Estan aca y no dentro de los casos de uso porque son **umbrales**, y un umbral
 * dentro del codigo es una constante que nadie puede cambiar sin desplegar (invariante
 * 10). Que vivan en un bean las deja a la vista y sustituibles.
 *
 * <p>Los cortes de confianza no son opinables: cada uno tiene su nombre publicado, y ese
 * nombre es lo que ve un tercero en un certificado. Moverlos cambia lo que significa
 * «confiable» para todo el mundo a la vez.
 */
@Configuration
public class ConfiguracionDeTransparencia {

    @Bean
    public List<PuntajeDeReputacion.Corte> escalaDeConfianza(@Value("${aportaya.reputacion.cortes}") String cortes) {
        return Stream.of(cortes.split(","))
                .map(String::trim)
                .map(par -> par.split(":"))
                .map(par -> new PuntajeDeReputacion.Corte(par[0], new BigDecimal(par[1])))
                .toList();
    }

    /**
     * Cuanto se atenua una opinion.
     *
     * <p>La mitad cuando hay conflicto declarado y la mitad cuando el autor ya reseno
     * mucho: quien opina de todos opina de nadie. El corte de volumen esta en cinco.
     */
    @Bean
    public ModeracionDeResena.Atenuacion atenuacionDeResena(
            @Value("${aportaya.resenas.atenuacion-por-conflicto}") String porConflicto,
            @Value("${aportaya.resenas.atenuacion-por-volumen}") String porVolumen,
            @Value("${aportaya.resenas.corte-de-volumen}") int corteDeVolumen) {
        return new ModeracionDeResena.Atenuacion(
                new BigDecimal(porConflicto), new BigDecimal(porVolumen), corteDeVolumen);
    }

    /** De cuanto se paso una metrica sale la severidad, no de una tabla de humor. */
    @Bean
    public SenalDeRiesgo.Escala escalaDeRiesgo(
            @Value("${aportaya.riesgo.hasta-alto}") String hastaAlto,
            @Value("${aportaya.riesgo.hasta-medio}") String hastaMedio,
            @Value("${aportaya.riesgo.exceso-critico}") String excesoCritico,
            @Value("${aportaya.riesgo.exceso-alto}") String excesoAlto,
            @Value("${aportaya.riesgo.exceso-medio}") String excesoMedio) {
        return new SenalDeRiesgo.Escala(
                new BigDecimal(hastaAlto),
                new BigDecimal(hastaMedio),
                new BigDecimal(excesoCritico),
                new BigDecimal(excesoAlto),
                new BigDecimal(excesoMedio));
    }

    /** Azar criptografico: el codigo de un certificado no se adivina. */
    @Bean
    public SecureRandom azar() {
        return new SecureRandom();
    }
}
