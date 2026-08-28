package bo.aportaya.publicidad.dominio;

import java.util.Optional;
import java.util.UUID;

/**
 * A quien pertenece una cuenta de anunciante: a un organizador de la plataforma o a un
 * negocio de afuera. Nunca a los dos, nunca a ninguno (R-PUB-01).
 *
 * <p>La exclusividad no es un capricho del modelo. Si un anunciante pudiera colgar de
 * un organizador y de un socio comercial a la vez, el gasto de una campana tendria dos
 * dueños posibles y la liquidacion del mes podria cobrarsele a cualquiera de los dos.
 */
public record ReferenciaDelAnunciante(String tipo, UUID organizadorId, UUID socioComercialId) {

    public static final String ORGANIZADOR = "ORGANIZADOR";
    public static final String SOCIO_COMERCIAL = "SOCIO_COMERCIAL";

    /** Que dice la referencia, o por que no dice nada. */
    public enum Veredicto {
        ORGANIZADOR_VALIDO,
        SOCIO_COMERCIAL_VALIDO,
        TIPO_DESCONOCIDO,
        SIN_REFERENCIA,
        DOS_REFERENCIAS,
        REFERENCIA_AJENA_AL_TIPO
    }

    public Veredicto veredicto() {
        boolean tieneOrganizador = organizadorId != null;
        boolean tieneSocio = socioComercialId != null;
        if (tieneOrganizador && tieneSocio) {
            return Veredicto.DOS_REFERENCIAS;
        }
        if (!tieneOrganizador && !tieneSocio) {
            return Veredicto.SIN_REFERENCIA;
        }
        if (ORGANIZADOR.equals(tipo)) {
            return tieneOrganizador ? Veredicto.ORGANIZADOR_VALIDO : Veredicto.REFERENCIA_AJENA_AL_TIPO;
        }
        if (SOCIO_COMERCIAL.equals(tipo)) {
            return tieneSocio ? Veredicto.SOCIO_COMERCIAL_VALIDO : Veredicto.REFERENCIA_AJENA_AL_TIPO;
        }
        return Veredicto.TIPO_DESCONOCIDO;
    }

    public boolean esValida() {
        Veredicto v = veredicto();
        return v == Veredicto.ORGANIZADOR_VALIDO || v == Veredicto.SOCIO_COMERCIAL_VALIDO;
    }

    /** El identificador que hay que ir a comprobar antes de escribir. */
    public Optional<UUID> aComprobar() {
        return Optional.ofNullable(ORGANIZADOR.equals(tipo) ? organizadorId : socioComercialId);
    }
}
