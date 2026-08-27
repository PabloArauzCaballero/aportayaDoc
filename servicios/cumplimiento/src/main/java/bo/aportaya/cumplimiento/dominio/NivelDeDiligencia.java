package bo.aportaya.cumplimiento.dominio;

import java.util.List;

/**
 * Los cinco niveles de {@code ck_debida_diligencia_tipo}, **en orden**.
 *
 * <p>El orden es la razon de ser del enum: CU-02 solo eleva, nunca baja. Bajar de
 * nivel por la via de «elevar» dejaria a alguien con los topes de un nivel que ya
 * no le corresponde, y sin que nadie lo haya decidido.
 */
public enum NivelDeDiligencia {
    SIMPLIFICADA,
    ESTANDAR,
    AMPLIADA,
    REFORZADA,
    CONTINUA;

    public boolean esSuperiorA(NivelDeDiligencia otro) {
        return ordinal() > otro.ordinal();
    }

    /** Los niveles que el contrato de CU-02 admite como destino. */
    public static List<NivelDeDiligencia> destinosValidos() {
        return List.of(ESTANDAR, AMPLIADA, REFORZADA);
    }
}
