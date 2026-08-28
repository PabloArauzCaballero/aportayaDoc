package bo.aportaya.transparencia.dominio.puertos;

import bo.aportaya.transparencia.aplicacion.CU61VerificarSorteo.PaqueteDeSorteo;
import java.util.Optional;
import java.util.UUID;

/**
 * De donde sale el paquete que CU-61 recomputa.
 *
 * <p>El sorteo vive en {@code grupos.sorteo_turnos} y este servicio no lee ese esquema
 * (invariante 11): lo pide por el contrato de {@code grupos}.
 *
 * <p><b>Hueco declarado.</b> {@code GET /publico/sorteos/{id}/verificacion} es una de
 * las cuatro rutas sin sesion del sistema (ADR-024), y la ruta de {@code grupos} que
 * publica el paquete exige sesion. Un tercero SIN cuenta —el destinatario de esta
 * ruta— recibe hoy «no verificable». Cerrarlo requiere tocar la boveda, y eso no se
 * decide en un carril: o el sorteo se sella en {@code transparencia.registro_sellado}
 * —cuyo {@code CHECK} de {@code tipo_entidad} hoy no admite {@code SORTEO}—, o ADR-024
 * admite una quinta ruta publica. Las dos son cambios troncales.
 */
public interface PaquetesDeSorteo {

    /** El paquete publicado, o vacio si no se pudo obtener. */
    Optional<PaqueteDeSorteo> de(UUID sorteoId);
}
