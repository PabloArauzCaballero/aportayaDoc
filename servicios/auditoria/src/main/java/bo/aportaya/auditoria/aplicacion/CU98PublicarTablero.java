package bo.aportaya.auditoria.aplicacion;

import bo.aportaya.auditoria.dominio.SentidoDeMeta;
import bo.aportaya.auditoria.dominio.Variacion;
import bo.aportaya.auditoria.infraestructura.IndicadorRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-98 · Publicar el tablero de indicadores.
 *
 * <p>Que la direccion mire siempre los mismos numeros, calculados de la misma forma,
 * con su meta al lado — para discutir que hacer en vez de discutir de donde salio la
 * cifra.
 *
 * <p><b>Esta clase no calcula ningun indicador.</b> Lee lo que el trabajo de calculo
 * dejo en {@code indicador_kpi}, unido a la {@code definicion_indicador} con la que se
 * calculo, y decide tres cosas de publicacion: si cumple la meta, si el valor se puede
 * mostrar sin identificar personas, y como se lee la variacion. Calcular aca crearia
 * un segundo lugar donde nace el mismo numero, y entonces hay dos numeros.
 *
 * <p><b>La transaccion es de solo lectura</b> y existe igual: {@code conContexto}
 * exige una transaccion abierta para que {@code SET LOCAL} fije el contexto de RLS.
 * Sin eso, la consulta correria sin politica de fila y devolveria indicadores de
 * dimensiones que quien pregunta no puede ver — sin error y sin rastro.
 */
@Service
public class CU98PublicarTablero {

    private final Datos datos;
    private final IndicadorRepositorio indicadores;

    public CU98PublicarTablero(Datos datos, IndicadorRepositorio indicadores) {
        this.datos = datos;
        this.indicadores = indicadores;
    }

    @Transactional(readOnly = true)
    public SalidaTablero ejecutar(EntradaTablero entrada, ContextoSesion ctx) {
        Dimension dimension = Dimension.de(entrada.dimension());

        if (dimension != Dimension.GLOBAL && entrada.dimensionId().isEmpty()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(98, 2), "La dimension " + dimension + " necesita el identificador de a quien mira.");
        }

        UUID dimensionId = entrada.dimensionId().orElse(null);

        return datos.conContexto(ctx, dsl -> {
            List<Indicador> publicados =
                    indicadores.delPeriodo(dsl, entrada.periodo(), dimension.name(), dimensionId).stream()
                            .map(fila -> publicar(dsl, fila, entrada, dimension, dimensionId))
                            .toList();

            // Provisorio es del tablero entero y no de cada tarjeta: si un solo
            // indicador del periodo se calculo sin cuadrar, la lectura de todos queda
            // condicionada. Decirlo una vez arriba evita que alguien cite el unico que
            // estaba definitivo como si el resto tambien lo estuviera.
            boolean provisorio = publicados.stream().anyMatch(Indicador::provisorio);

            return new SalidaTablero(
                    entrada.periodo(), dimension.name(), entrada.dimensionId(), provisorio, publicados);
        });
    }

    private Indicador publicar(
            org.jooq.DSLContext dsl,
            IndicadorRepositorio.Fila fila,
            EntradaTablero entrada,
            Dimension dimension,
            UUID dimensionId) {

        // La variacion guardada manda; si el calculo no la dejo, se deriva del periodo
        // anterior. Nunca se inventa un cero.
        Optional<BigDecimal> variacion = fila.variacion().isPresent()
                ? fila.variacion()
                : Variacion.entre(
                        fila.valor(),
                        indicadores.anteriorA(dsl, fila.codigo(), dimension.name(), dimensionId, entrada.periodo()));

        // Sin meta no hay semaforo. `Optional` vacio y no `false`: decir «no cumple»
        // sobre un indicador que nadie se comprometio a mover es inventar un rojo.
        Optional<Boolean> cumpleMeta = fila.meta()
                .map(meta -> SentidoDeMeta.valueOf(fila.sentidoMeta()).cumple(fila.valor(), meta));

        boolean suprimido = suprimirPorMinimo(fila);

        List<PuntoDeSerie> serie = suprimido
                // Si el valor se suprime, la serie tambien: publicarla seria dar el
                // numero por la puerta de atras, y la supresion quedaria decorativa.
                ? List.of()
                : indicadores
                        .serieDe(
                                dsl,
                                fila.codigo(),
                                dimension.name(),
                                dimensionId,
                                entrada.periodo(),
                                entrada.periodosDeSerie())
                        .stream()
                        .map(punto -> new PuntoDeSerie(punto.periodo(), punto.valor()))
                        .toList();

        return new Indicador(
                fila.codigo(),
                fila.nombre(),
                suprimido ? Optional.empty() : Optional.of(fila.valor()),
                fila.unidad(),
                fila.meta(),
                suprimido ? Optional.empty() : cumpleMeta,
                suprimido ? Optional.empty() : variacion,
                serie,
                suprimido,
                fila.casos(),
                fila.minimoCasos(),
                fila.provisorio(),
                fila.familia(),
                fila.duenoFamilia(),
                fila.definicionVersion(),
                fila.calculadoEn());
    }

    /**
     * Un promedio de tres personas identifica a las tres (`R-SEG-03`).
     *
     * <p>Sin {@code casos} no se puede saber si la muestra alcanza, y **la duda se
     * resuelve suprimiendo**: publicar un valor cuyo tamano de muestra se desconoce es
     * apostar con datos de personas.
     */
    private static boolean suprimirPorMinimo(IndicadorRepositorio.Fila fila) {
        if (fila.minimoCasos() <= 0) {
            return false;
        }
        return fila.casos().map(casos -> casos < fila.minimoCasos()).orElse(true);
    }

    /** Las tres que admite {@code ck_indicador_kpi_dimension}. Manda la tabla. */
    public enum Dimension {
        GLOBAL,
        POR_GRUPO,
        POR_ORGANIZADOR;

        static Dimension de(String valor) {
            try {
                return Dimension.valueOf(valor);
            } catch (IllegalArgumentException noExiste) {
                throw new ErrorDeNegocio(
                        CodigoError.de(98, 2),
                        "La dimension '" + valor + "' no existe: son GLOBAL, POR_GRUPO y POR_ORGANIZADOR.");
            }
        }
    }

    /** Un punto de la serie, en el lenguaje del caso de uso y no en el de la consulta. */
    public record PuntoDeSerie(String periodo, BigDecimal valor) {}

    public record EntradaTablero(String periodo, String dimension, Optional<UUID> dimensionId, int periodosDeSerie) {}

    public record SalidaTablero(
            String periodo,
            String dimension,
            Optional<UUID> dimensionId,
            boolean provisorio,
            List<Indicador> indicadores) {}

    public record Indicador(
            String codigo,
            String nombre,
            Optional<BigDecimal> valor,
            String unidad,
            Optional<BigDecimal> meta,
            Optional<Boolean> cumpleMeta,
            Optional<BigDecimal> variacionPeriodoAnterior,
            List<PuntoDeSerie> serie,
            boolean suprimidoPorPrivacidad,
            Optional<Integer> casos,
            int minimoCasos,
            boolean provisorio,
            String familia,
            String duenoFamilia,
            String definicionVersion,
            OffsetDateTime calculadoEn) {}
}
