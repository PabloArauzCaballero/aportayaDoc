package bo.aportaya.identidad.aplicacion;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la pagina traduce desde el cuerpo HTTP. Sin tipos de Spring y sin nada de la
 * capa web: es lo que permite probar el organismo sin levantar un servidor.
 */
public record EntradaAutenticacion(
        String telefonoE164,
        char[] credencial,
        String huella,
        String plataforma,
        String ip,
        String agente,
        String trazaId,
        Optional<String> factorPresentado,
        boolean operacionSensible,
        Duration vigenciaDeSesion) {

    /** El «usuario» del contexto mientras todavia no hay usuario autenticado. */
    public static final UUID PROCESO_INGRESO = UUID.fromString("00000000-0000-4000-8000-000000000004");
}
