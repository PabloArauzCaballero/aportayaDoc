package bo.aportaya.organizador.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;

/**
 * En que escalon esta un organizador, y cuanto puede administrar desde ahi.
 *
 * <p>Los cuatro que admite {@code ck_organizador_nivel}. El nivel no es una medalla:
 * define **cuanta plata ajena** puede tener en curso a la vez. Subir de nivel a quien
 * todavia no lo sostiene es la forma mas directa de que un incumplimiento se lleve
 * puesto a varios grupos en vez de a uno.
 */
public enum NivelDeOrganizador {
    APRENDIZ,
    ESTANDAR,
    SENIOR,
    MAESTRO;

    private static final List<NivelDeOrganizador> ESCALERA = List.of(APRENDIZ, ESTANDAR, SENIOR, MAESTRO);

    public static NivelDeOrganizador exigir(String nombre) {
        try {
            return valueOf(nombre);
        } catch (IllegalArgumentException | NullPointerException noEsUno) {
            throw new ErrorDeNegocio(CodigoError.de(92, 4), "Nivel de organizador no admitido: " + nombre + ".");
        }
    }

    /** Cuantos escalones hay entre este nivel y otro. Negativo si es un descenso. */
    public int distanciaHasta(NivelDeOrganizador otro) {
        return ESCALERA.indexOf(otro) - ESCALERA.indexOf(this);
    }

    /**
     * Un ascenso salta **un escalon por vez**.
     *
     * <p>Saltar dos de golpe le entrega a alguien un limite que nunca sostuvo, y el
     * historial que probaria que puede sostenerlo es justamente el que no tiene.
     * Bajar si puede ser de golpe: cuando algo sale mal, esperar no mejora nada.
     */
    public boolean admiteMoverseA(NivelDeOrganizador destino) {
        return distanciaHasta(destino) <= 1;
    }

    /** El limite de monto administrado del nivel, tomado del catalogo. */
    public static BigDecimal limiteDe(List<BigDecimal> limitesPorNivel, NivelDeOrganizador nivel) {
        return limitesPorNivel.get(ESCALERA.indexOf(nivel));
    }
}
