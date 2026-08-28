package bo.aportaya.transparencia.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Evalua el criterio publicado de cada insignia contra los hechos del usuario.
 *
 * <p>**No se otorga por pedido.** Toda insignia se verifica contra los datos, y una
 * insignia cuyo codigo esta en el catalogo pero no tiene regla aca **no se otorga
 * nunca** (invariante 9: denegar por omision). La alternativa —otorgarla porque
 * alguien la pidio— convierte el catalogo de logros en un catalogo de favores.
 *
 * <p>Los hechos vienen de fuera: viven en los esquemas de aportes, garantia,
 * identidad y organizador, y aca no se leen (invariante 11).
 *
 * <p>Devuelve tambien **cuanto falta**. Es la parte que sirve: una insignia bloqueada
 * sin decir que falta es una promesa vaga; «te faltan 3 aportes puntuales» es algo que
 * alguien puede hacer esta semana.
 */
public final class CriterioDeInsignia {

    private CriterioDeInsignia() {}

    /**
     * @param desempenoMinimoDeOrganizador el corte que publica el criterio de
     *     ORGANIZADOR_CONFIABLE. Llega como dato: mover la vara de un logro publicado
     *     no puede exigir un despliegue.
     */
    public static Evaluacion evaluar(String codigoInsignia, Hechos h, BigDecimal desempenoMinimoDeOrganizador) {
        return switch (codigoInsignia) {
            case "PRIMER_PASANAKU" ->
                new Evaluacion(
                        h.ciclosCompletadosSinCobertura() >= 1,
                        h.ciclosCompletadosSinCobertura(),
                        1,
                        "Un ciclo completo con la entrega cobrada y sin cobertura del fondo.");
            case "PAGADOR_PUNTUAL" ->
                new Evaluacion(
                        h.aportesPuntualesConsecutivos() >= 12,
                        h.aportesPuntualesConsecutivos(),
                        12,
                        "Doce aportes seguidos en fecha o antes.");
            case "SIN_MORA_12_MESES" ->
                new Evaluacion(
                        h.diasCorridosSinMora() >= 365,
                        h.diasCorridosSinMora(),
                        365,
                        "Un año corrido sin un dia de atraso ni recargo por mora.");
            case "TRES_CICLOS" ->
                new Evaluacion(
                        h.ciclosCompletados() >= 3,
                        h.ciclosCompletados(),
                        3,
                        "Tres ciclos terminados de principio a fin.");
            case "REGULARIZADOR" ->
                new Evaluacion(
                        h.regularizacionesCumplidasEnPlazo() >= 1,
                        h.regularizacionesCumplidasEnPlazo(),
                        1,
                        "Una deuda regularizada dentro del plazo comprometido.");
            case "ORGANIZADOR_CONFIABLE" -> {
                // Tres condiciones, no una: cerrar dos grupos no basta si hubo sancion
                // firme o si las entregas salieron tarde.
                boolean limpio = h.sancionesFirmesComoOrganizador() == 0
                        && h.entregasFueraDePlazoComoOrganizador() == 0
                        && h.desempenoComoOrganizador() != null
                        && h.desempenoComoOrganizador().compareTo(desempenoMinimoDeOrganizador) >= 0;
                yield new Evaluacion(
                        limpio && h.gruposCerradosComoOrganizador() >= 2,
                        limpio ? h.gruposCerradosComoOrganizador() : 0,
                        2,
                        "Dos grupos cerrados sin sancion, sin entrega tardia y con desempeño de 80 o mas.");
            }
            case "REFERENTE" ->
                new Evaluacion(
                        h.invitadosQueCompletaronUnCiclo() >= 5,
                        h.invitadosQueCompletaronUnCiclo(),
                        5,
                        "Cinco invitados que completaron un ciclo sin incumplimientos.");
            case "VETERANO" -> {
                boolean alDia = h.identidadVigente() && !h.tieneDeudaCastigada();
                yield new Evaluacion(
                        alDia && h.mesesDeAntiguedad() >= 24,
                        alDia ? h.mesesDeAntiguedad() : 0,
                        24,
                        "Dos años con la cuenta activa, identidad vigente y sin deuda castigada.");
            }
            default -> Evaluacion.sinRegla();
        };
    }

    /**
     * Las insignias que un hecho puede haber desbloqueado. Filtra el catalogo para no
     * reevaluar las ocho ante cualquier cosa que pase.
     */
    public static List<String> afectadasPor(String tipoDeEvento) {
        return switch (tipoDeEvento) {
            case "CICLO_COMPLETADO" -> List.of("PRIMER_PASANAKU", "TRES_CICLOS", "REFERENTE");
            case "APORTE_PUNTUAL", "APORTE_ANTICIPADO" -> List.of("PAGADOR_PUNTUAL", "SIN_MORA_12_MESES");
            case "MES_DE_ANTIGUEDAD" -> List.of("VETERANO");
            case "PLAN_REGULARIZACION_CUMPLIDO", "DEUDA_REGULARIZADA" -> List.of("REGULARIZADOR");
            case "GRUPO_ADMINISTRADO_SIN_INCIDENCIAS" -> List.of("ORGANIZADOR_CONFIABLE");
            default -> List.of();
        };
    }

    /**
     * @param avance cuanto lleva
     * @param meta cuanto necesita
     */
    public record Evaluacion(boolean cumple, int avance, int meta, String motivoLegible) {

        /** Una insignia sin regla no se otorga, y se dice por que. */
        public static Evaluacion sinRegla() {
            return new Evaluacion(false, 0, 0, "Esta insignia no tiene criterio evaluable declarado.");
        }

        public boolean tieneRegla() {
            return meta > 0;
        }

        public Optional<String> cuantoFalta() {
            if (cumple || !tieneRegla()) {
                return Optional.empty();
            }
            return Optional.of("Te faltan " + (meta - avance) + " de " + meta + ".");
        }
    }

    /** Los hechos del usuario, resueltos por los servicios que los poseen. */
    public record Hechos(
            int ciclosCompletados,
            int ciclosCompletadosSinCobertura,
            int aportesPuntualesConsecutivos,
            int diasCorridosSinMora,
            int regularizacionesCumplidasEnPlazo,
            int gruposCerradosComoOrganizador,
            int sancionesFirmesComoOrganizador,
            int entregasFueraDePlazoComoOrganizador,
            BigDecimal desempenoComoOrganizador,
            int invitadosQueCompletaronUnCiclo,
            int mesesDeAntiguedad,
            boolean identidadVigente,
            boolean tieneDeudaCastigada) {}
}
