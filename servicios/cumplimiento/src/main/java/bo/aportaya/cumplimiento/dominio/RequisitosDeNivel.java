package bo.aportaya.cumplimiento.dominio;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU-02 · Compara los documentos que el nivel exige contra los que llegaron. Puro.
 *
 * <p>**Hueco declarado.** La boveda no trae un catalogo de documentos por nivel: la
 * columna {@code documentos_requeridos} guarda la lista en cada fila, pero nadie dice
 * de donde sale. Se declara en configuracion —donde se ve y se audita— en vez de
 * hornearse en el codigo, que es lo que la regla cero prohibe.
 */
public record RequisitosDeNivel(Map<NivelDeDiligencia, List<String>> porNivel) {

    public RequisitosDeNivel {
        porNivel = Map.copyOf(porNivel);
    }

    public List<String> exigidosPara(NivelDeDiligencia nivel) {
        return porNivel.getOrDefault(nivel, List.of());
    }

    /**
     * Lo que falta, en el orden en que se pidio.
     *
     * <p>El cotejo es por tipo de documento y no por cantidad: dos copias del mismo
     * papel no cubren dos requisitos distintos.
     */
    public List<String> faltantes(NivelDeDiligencia destino, List<String> tiposRecibidos) {
        Set<String> recibidos = new LinkedHashSet<>(tiposRecibidos);
        return exigidosPara(destino).stream()
                .filter(exigido -> !recibidos.contains(exigido))
                .toList();
    }
}
