package bo.aportaya.grupos.dominio;

import bo.aportaya.plataforma.dominio.Dinero;

/**
 * Lo que el invitado ve, y **solo** eso.
 *
 * <p>El mensaje dice quien invita y a que grupo, con el monto y la periodicidad:
 * nadie deberia aceptar un compromiso de dinero sin saber cual. Y no dice nada mas —
 * ni nombres de los otros integrantes, ni telefonos, ni montos individuales—. Quien
 * invita ya conoce al invitado; el sistema no tiene por que presentar a nadie mas.
 */
public record MensajeDeInvitacion(
        String nombreDeQuienInvita, String nombreDelGrupo, Dinero montoDelAporte, String periodicidad) {

    public String texto() {
        return "%s te invita a %s: %s %s cada periodo, %s."
                .formatted(
                        nombreDeQuienInvita,
                        nombreDelGrupo,
                        montoDelAporte.moneda(),
                        montoDelAporte,
                        periodicidad.toLowerCase(java.util.Locale.ROOT));
    }
}
