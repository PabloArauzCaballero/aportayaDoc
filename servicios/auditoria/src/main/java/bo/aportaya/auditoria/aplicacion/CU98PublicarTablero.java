package bo.aportaya.auditoria.aplicacion;

import bo.aportaya.auditoria.dominio.CatalogoDeDefiniciones;
import bo.aportaya.auditoria.dominio.DefinicionDeIndicador;
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
 * dejo en {@code indicador_kpi} y le agrega lo que hace falta para interpretarlo:
 * la familia, el dueno, si cumple la meta y con que version de la definicion se
 * calculo. Calcular aca crearia un segundo lugar donde nace el mismo numero, y
 * entonces hay dos numeros.
 *
 * <p><b>La transaccion es de solo lectura</b> y existe igual: {@code conContexto}
 * exige una transaccion abierta para que {@code SET LOCAL} fije el contexto de RLS.
 * Sin eso, la consulta correria sin politica de fila y devolveria indicadores de
 * dimensiones que quien pregunta no puede ver.
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
        // AP-CU98-02 antes de tocar la base: una dimension que la tabla no admite no
        // es «cero indicadores», es una consulta mal formada.
        Dimension dimension = Dimension.de(entrada.dimension());

        if (dimension != Dimension.GLOBAL && entrada.dimensionId().isEmpty()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(98, 2), "La dimension " + dimension + " necesita el identificador de a quien mira.");
        }

        UUID dimensionId = entrada.dimensionId().orElse(null);

        return datos.conContexto(ctx, dsl -> {
            List<IndicadorRepositorio.Fila> filas =
                    indicadores.delPeriodo(dsl, entrada.periodo(), dimension.name(), dimensionId);

            // AP-CU98-02. CU-98 pone la definicion escrita como PRECONDICION, no como
            // adorno: un numero que nadie sabe interpretar no se puede discutir en un
            // comite. Se corta el tablero entero y se nombran los codigos que faltan,
            // en vez de publicar una fila muda entre nueve que se entienden.
            List<String> sinDefinicion = filas.stream()
                    .map(IndicadorRepositorio.Fila::codigo)
                    .filter(codigo -> CatalogoDeDefiniciones.de(codigo).isEmpty())
                    .toList();
            if (!sinDefinicion.isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(98, 2),
                        "Estos indicadores no tienen definicion escrita: " + String.join(", ", sinDefinicion));
            }

            List<Indicador> publicados = filas.stream()
                    .map(fila -> publicar(dsl, fila, entrada, dimension, dimensionId))
                    .toList();

            return new SalidaTablero(entrada.periodo(), dimension.name(), entrada.dimensionId(), publicados);
        });
    }

    private Indicador publicar(
            org.jooq.DSLContext dsl,
            IndicadorRepositorio.Fila fila,
            EntradaTablero entrada,
            Dimension dimension,
            UUID dimensionId) {

        // Existe: lo garantizo la comprobacion de arriba, antes de publicar nada.
        DefinicionDeIndicador definicion = CatalogoDeDefiniciones.de(fila.codigo())
                .orElseThrow(() -> new IllegalStateException("definicion perdida para " + fila.codigo()));

        // La variacion guardada manda; si el calculo no la dejo, se deriva del
        // periodo anterior. Nunca se inventa un cero.
        Optional<BigDecimal> variacion = fila.variacion().isPresent()
                ? fila.variacion()
                : Variacion.entre(
                        fila.valor(),
                        indicadores.anteriorA(dsl, fila.codigo(), dimension.name(), dimensionId, entrada.periodo()));

        // Sin meta no hay semaforo. `Optional` vacio y no `false`: decir «no cumple»
        // sobre un indicador que nadie se comprometio a mover es inventar un rojo.
        Optional<Boolean> cumpleMeta =
                fila.meta().map(meta -> definicion.sentido().cumple(fila.valor(), meta));

        // La serie se traduce ACA y no en el controlador: el tipo del repositorio no
        // sale de la capa de infraestructura. ArchUnit lo hace cumplir, y tiene razon
        // — dejarlo pasar ata la forma de la respuesta HTTP a la forma de una consulta.
        List<PuntoDeSerie> serie = indicadores
                .serieDe(
                        dsl, fila.codigo(), dimension.name(), dimensionId, entrada.periodo(), entrada.periodosDeSerie())
                .stream()
                .map(punto -> new PuntoDeSerie(punto.periodo(), punto.valor()))
                .toList();

        return new Indicador(
                fila.codigo(),
                fila.nombre(),
                fila.valor(),
                fila.unidad(),
                fila.meta(),
                cumpleMeta,
                variacion,
                serie,
                definicion.familia().name(),
                definicion.duenoFamilia(),
                definicion.version(),
                fila.calculadoEn());
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
            String periodo, String dimension, Optional<UUID> dimensionId, List<Indicador> indicadores) {}

    public record Indicador(
            String codigo,
            String nombre,
            BigDecimal valor,
            String unidad,
            Optional<BigDecimal> meta,
            Optional<Boolean> cumpleMeta,
            Optional<BigDecimal> variacionPeriodoAnterior,
            List<PuntoDeSerie> serie,
            String familia,
            String duenoFamilia,
            String definicionVersion,
            OffsetDateTime calculadoEn) {}
}
