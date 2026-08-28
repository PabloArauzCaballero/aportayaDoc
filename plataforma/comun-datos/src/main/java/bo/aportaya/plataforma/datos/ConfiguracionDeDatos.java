package bo.aportaya.plataforma.datos;

import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

/**
 * El {@link DSLContext} que usan los catorce servicios.
 *
 * <p>El {@link TransactionAwareDataSourceProxy} es la pieza que no se puede omitir:
 * sin el, jOOQ pide una conexion nueva al pool y las consultas corren FUERA de la
 * transaccion que acaba de hacer {@code SET LOCAL}. El sintoma no es un error: es que
 * la politica de fila no aplica y se ve todo.
 */
@Configuration
public class ConfiguracionDeDatos {

    @Bean
    @ConditionalOnMissingBean
    public DSLContext dslContext(DataSource fuente) {
        DefaultConfiguration configuracion = new DefaultConfiguration();
        configuracion.set(new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(fuente)));
        configuracion.set(SQLDialect.POSTGRES);
        return new DefaultDSLContext(configuracion);
    }

    @Bean
    @ConditionalOnMissingBean
    public Datos datos(DSLContext dsl) {
        return new Datos(dsl);
    }

    /**
     * El calendario habil, uno para los catorce.
     *
     * <p>Los feriados son un dato sembrado con alcance, no una constante: por eso se
     * inyecta y no se calcula. Quien necesite otro —una prueba que quiera todos los dias
     * habiles— declara el suyo y gana.
     */
    @Bean
    @ConditionalOnMissingBean
    public bo.aportaya.plataforma.dominio.CalendarioHabil calendarioHabil(DSLContext dsl) {
        return new CalendarioDelCatalogo(dsl);
    }

    /**
     * La transaccion aparte, para escribir cuando la de afuera ya no sirve.
     *
     * <p>La usa quien tiene que dejar constancia de un fallo: una consulta cortada por
     * tiempo deja la transaccion invalida, y escribir el rastro ahi adentro no funciona.
     */
    @Bean
    @ConditionalOnMissingBean
    public TransaccionAparte transaccionAparte(org.springframework.transaction.PlatformTransactionManager gestor) {
        return new TransaccionAparte(gestor);
    }
}
