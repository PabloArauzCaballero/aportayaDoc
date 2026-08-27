package bo.aportaya.auditoria.dominio;

/**
 * Que es un indicador, mas alla de su numero.
 *
 * <p>**Un indicador es una definicion, no una consulta.** El valor lo calcula el
 * trabajo que llena `indicador_kpi`; esto es lo que permite interpretarlo: a que
 * familia pertenece, quien responde por el, para que lado se cumple su meta y con
 * que version de la definicion se calculo.
 *
 * <p>La `version` es lo que hace reproducible un numero de hace un ano: si la formula
 * cambia, cambia la version y la serie vieja conserva la suya. Un tablero donde una
 * metrica mejoro un 40 % porque cambio la formula es peor que no tener tablero.
 */
public record DefinicionDeIndicador(
        String codigo, FamiliaDeIndicador familia, String duenoFamilia, SentidoDeMeta sentido, String version) {

    public DefinicionDeIndicador {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("una definicion sin codigo no identifica nada");
        }
        if (duenoFamilia == null || duenoFamilia.isBlank()) {
            throw new IllegalArgumentException("un indicador sin dueno es un numero que nadie defiende");
        }
    }
}
