package bo.aportaya.grupos.dominio;

import java.util.Optional;
import java.util.UUID;

/**
 * A quien le aplica un dia no habil.
 *
 * <p>Un feriado de alcance {@code GRUPO} sin grupo no aplica a nadie y aplica a
 * todos a la vez: por eso el ambito y su referencia van juntos o no van.
 */
public enum AlcanceDeCalendario {
    NACIONAL,
    DEPARTAMENTAL,
    PLATAFORMA,
    GRUPO;

    public boolean completoCon(Optional<UUID> referencia) {
        return this == GRUPO ? referencia.isPresent() : true;
    }
}
