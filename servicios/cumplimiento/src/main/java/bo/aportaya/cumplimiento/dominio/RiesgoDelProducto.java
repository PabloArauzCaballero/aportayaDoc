package bo.aportaya.cumplimiento.dominio;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Evalua el riesgo LGI/FT de un producto antes de habilitarlo.
 *
 * <p>**Los cuatro factores son obligatorios**: cliente, producto, canal y geografia. No
 * es burocracia — evaluar tres de cuatro es no haber mirado el cuarto, y el que no se
 * mira es siempre el que despues explota.
 *
 * <p>**Un riesgo residual alto sin control asociado impide aprobar.** Escribir el riesgo
 * y no escribir que se hace con el convierte la matriz en una lista de disculpas
 * anticipadas.
 */
public final class RiesgoDelProducto {

    public enum Factor {
        CLIENTE,
        PRODUCTO,
        CANAL,
        GEOGRAFIA
    }

    private RiesgoDelProducto() {}

    /**
     * @param riesgos al menos uno por factor
     * @param controles por indice de riesgo; un riesgo puede tener varios
     */
    public static Evaluacion evaluar(List<Riesgo> riesgos, Map<Integer, List<String>> controles, Escala escala) {
        var vistos = EnumSet.noneOf(Factor.class);
        riesgos.forEach(r -> vistos.add(r.factor()));
        var faltantes = EnumSet.allOf(Factor.class);
        faltantes.removeAll(vistos);
        if (!faltantes.isEmpty()) {
            return Evaluacion.faltaFactor(faltantes.stream().map(Enum::name).toList());
        }

        var sinControl = new java.util.ArrayList<String>();
        int maximo = Integer.MIN_VALUE;
        for (int i = 0; i < riesgos.size(); i++) {
            var r = riesgos.get(i);
            int inherente = r.probabilidad() * r.impacto();
            maximo = Math.max(maximo, inherente);
            // Solo el riesgo ALTO exige control declarado. Pedirlo para todo haria que
            // la matriz se llenara de controles de adorno y el alto se perdiera entre
            // ellos.
            if (inherente >= escala.desdeAlto()
                    && controles.getOrDefault(i, List.of()).isEmpty()) {
                sinControl.add(r.descripcion());
            }
        }
        if (!sinControl.isEmpty()) {
            return new Evaluacion(false, null, List.of(), sinControl, List.of());
        }
        return new Evaluacion(true, nivel(maximo, escala), reglasSugeridas(riesgos, escala), List.of(), List.of());
    }

    /**
     * Donde empieza cada nivel, sobre el producto de probabilidad por impacto (1..25).
     *
     * <p>Llega como dato y no como constante (invariante 10): mover el corte de «alto»
     * cambia que productos exigen control declarado, y esa palanca es de la politica de
     * riesgos, no del codigo.
     */
    public record Escala(int desdeAlto, int desdeMedio) {}

    private static String nivel(int inherenteMaximo, Escala escala) {
        if (inherenteMaximo >= escala.desdeAlto()) {
            return "ALTO";
        }
        return inherenteMaximo >= escala.desdeMedio() ? "MEDIO" : "BAJO";
    }

    /**
     * Reglas de monitoreo que este perfil de riesgo pide.
     *
     * <p>Sugerirlas es lo que conecta la evaluacion con lo que despues se vigila: una
     * matriz que no deriva en reglas es un documento que nadie vuelve a abrir.
     */
    private static List<String> reglasSugeridas(List<Riesgo> riesgos, Escala escala) {
        var reglas = new java.util.LinkedHashSet<String>();
        for (var r : riesgos) {
            if (r.probabilidad() * r.impacto() < escala.desdeMedio()) {
                continue;
            }
            switch (r.factor()) {
                case CLIENTE -> reglas.add("PERFIL_INCONSISTENTE");
                case PRODUCTO -> reglas.add("FRACCIONAMIENTO");
                case CANAL -> reglas.add("VELOCIDAD_INUSUAL");
                case GEOGRAFIA -> reglas.add("JURISDICCION_SENSIBLE");
            }
        }
        return List.copyOf(reglas);
    }

    public record Riesgo(Factor factor, String descripcion, int probabilidad, int impacto) {

        public Riesgo {
            if (probabilidad < 1 || probabilidad > 5 || impacto < 1 || impacto > 5) {
                throw new IllegalArgumentException("Probabilidad e impacto van de 1 a 5");
            }
        }
    }

    public record Evaluacion(
            boolean aprobable,
            String nivelLft,
            List<String> reglasSugeridas,
            List<String> riesgosSinControl,
            List<String> factoresFaltantes) {

        static Evaluacion faltaFactor(List<String> faltantes) {
            return new Evaluacion(false, null, List.of(), List.of(), faltantes);
        }
    }
}
