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
}
