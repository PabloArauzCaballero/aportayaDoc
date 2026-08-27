package bo.aportaya.notificaciones.dominio;

import java.util.Set;

/**
 * Los canales por los que puede salir un aviso.
 *
 * <p>**No todos estan encendidos.** El contrato de implementacion fija los defaults:
 * bandeja interna y correo, con push como aviso; WhatsApp y SMS **apagados**. Encender
 * un canal apagado es una de las dieciocho prohibiciones, asi que el enum los enumera
 * pero no los habilita: quien decide es la configuracion, y este tipo solo la aplica.
 *
 * <p>Los nombres son los de {@code ck_envio_notificacion_canal}, no los del texto del
 * caso de uso. La ficha dice «BANDEJA» y la base dice {@code IN_APP}; manda la base,
 * porque es la que rechaza. Ver el informe del carril: hueco H-1.
 */
public enum Canal {
    IN_APP,
    CORREO,
    PUSH,
    WHATSAPP,
    SMS,
    LLAMADA_VOZ;

    /**
     * Los que estan encendidos por omision.
     *
     * <p>Vive aca y no en un archivo de propiedades porque es el **piso**: la
     * configuracion puede apagar uno de estos, nunca encender uno que no este.
     */
    public static Set<Canal> encendidosPorOmision() {
        return Set.of(IN_APP, CORREO, PUSH);
    }

    public boolean apagadoPorOmision() {
        return !encendidosPorOmision().contains(this);
    }
}
