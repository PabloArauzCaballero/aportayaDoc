package bo.aportaya.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Invariante 1: <b>el esquema es de {@code sql/}, y las clases se generan de el.</b>
 *
 * <p>Si alguien agrega una columna y no regenera, el codigo no compila — eso lo
 * garantiza el compilador cuando alguien la USA. Lo que el compilador no ve es la
 * columna que se agrego, nadie usa todavia, y por lo tanto <b>nadie nota que las
 * clases quedaron viejas</b>: seis semanas despues aparece un {@code INSERT} que no
 * la contempla y el defecto se atribuye a quien lo escribio.
 *
 * <p>Esta prueba compara, tabla por tabla, las columnas generadas contra las de la
 * base viva. No reemplaza a la compilacion: cubre justo el hueco que la compilacion
 * deja.
 */
class EsquemaAlDiaRepositorioTest {

    private static final String ESQUEMA = "identidad";

    private static DSLContext dsl;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(fuente, SQLDialect.POSTGRES);
    }

    @Test
    @DisplayName("Las clases generadas de jOOQ dicen exactamente lo que la base tiene")
    void lasClasesGeneradasCoincidenConLaBase() {
        assertThat(tablasGeneradas())
                .as("no se genero ninguna clase: el gate seria vacio")
                .isNotEmpty();
        assertThat(divergencias()).isEmpty();
    }

    @Test
    @DisplayName("Y el gate no es vacuo: una columna que la base tiene de mas se detecta")
    void elGateDetectaLaDeriva() {
        // Se inyecta la deriva y se comprueba que aparece. Un gate que nunca vio
        // fallar no es un gate: es una prueba que pasa.
        dsl.execute("ALTER TABLE identidad.usuario ADD COLUMN columna_intrusa text");
        try {
            assertThat(columnasDe("usuario")).contains("columna_intrusa");
            assertThat(divergencias()).anySatisfy(d -> assertThat(d).contains("columna_intrusa"));
        } finally {
            dsl.execute("ALTER TABLE identidad.usuario DROP COLUMN columna_intrusa");
        }

        assertThat(divergencias()).isEmpty();
    }

    private List<String> divergencias() {
        List<String> encontradas = new ArrayList<>();
        for (Table<?> tabla : tablasGeneradas()) {
            Set<String> generadas = new TreeSet<>();
            for (Field<?> campo : tabla.fields()) {
                generadas.add(campo.getName());
            }
            Set<String> enLaBase = columnasDe(tabla.getName());
            if (enLaBase.isEmpty()) {
                encontradas.add("%s: generada pero no existe en la base".formatted(tabla.getName()));
                continue;
            }
            Set<String> soloEnLaBase = new TreeSet<>(enLaBase);
            soloEnLaBase.removeAll(generadas);
            Set<String> soloGeneradas = new TreeSet<>(generadas);
            soloGeneradas.removeAll(enLaBase);

            if (!soloEnLaBase.isEmpty()) {
                encontradas.add("%s: la base tiene %s y las clases no. Regenera con ./gradlew generateJooq"
                        .formatted(tabla.getName(), soloEnLaBase));
            }
            if (!soloGeneradas.isEmpty()) {
                encontradas.add("%s: las clases tienen %s y la base no. El esquema cambio debajo"
                        .formatted(tabla.getName(), soloGeneradas));
            }
        }
        return encontradas;
    }

    private List<Table<?>> tablasGeneradas() {
        return bo.aportaya.identidad.generado.Identidad.IDENTIDAD.getTables();
    }

    private Set<String> columnasDe(String tabla) {
        return new TreeSet<>(dsl.select(DSL.field("column_name", String.class))
                .from(DSL.table("information_schema.columns"))
                .where(DSL.field("table_schema").eq(ESQUEMA))
                .and(DSL.field("table_name").eq(tabla))
                .fetch(DSL.field("column_name", String.class)));
    }
}
