package bo.aportaya.organizador.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Que le falta a alguien para poder administrar la plata de otros.
 *
 * <p>Los requisitos son **catalogo**, no constantes (invariante 10): que hoy hagan
 * falta seis meses de antiguedad no significa que sea para siempre, y una constante
 * en el codigo convierte un cambio de politica en un despliegue.
 *
 * <p>Denegar por omision (invariante 9): un requisito obligatorio del que **no hay
 * dato** no se da por cumplido. Habilitar a un organizador «porque probablemente
 * cumple» es entregarle el dinero de un grupo entero sobre una suposicion.
 */
public final class RequisitosDeHabilitacion {

    private RequisitosDeHabilitacion() {}

    /** Un requisito del catalogo, tal como lo evalua esta pieza. */
    public record Requisito(String codigo, String tipo, BigDecimal valorMinimo, boolean obligatorio) {}

    /** Que falto, y por que. En texto, porque el postulante lo va a leer. */
    public record Faltante(String codigo, String motivo) {}

    public record Veredicto(boolean habilitable, List<Faltante> faltantes) {

        public boolean tieneFaltantes() {
            return !faltantes.isEmpty();
        }
    }

    /**
     * @param medidos lo que se sabe del postulante, por codigo de requisito
     */
    public static Veredicto evaluar(List<Requisito> requisitos, Map<String, BigDecimal> medidos) {
        List<Faltante> faltantes = new ArrayList<>();

        for (Requisito requisito : requisitos) {
            BigDecimal medido = medidos.get(requisito.codigo());
            if (medido == null) {
                if (requisito.obligatorio()) {
                    faltantes.add(new Faltante(
                            requisito.codigo(),
                            "Falta el dato de «" + requisito.codigo() + "»: no se da por cumplido"));
                }
                continue;
            }
            if (medido.compareTo(requisito.valorMinimo()) < 0) {
                faltantes.add(new Faltante(
                        requisito.codigo(),
                        "Tiene " + medido.toPlainString() + " y se exige "
                                + requisito.valorMinimo().toPlainString()));
            }
        }

        // Un faltante NO obligatorio se informa igual: el postulante tiene derecho a
        // saber que le falta para el siguiente escalon, no solo si entro o no.
        boolean habilitable = faltantes.stream()
                .noneMatch(f -> requisitos.stream().anyMatch(r -> r.codigo().equals(f.codigo()) && r.obligatorio()));
        return new Veredicto(habilitable, List.copyOf(faltantes));
    }
}
