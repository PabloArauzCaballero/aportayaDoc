package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.infraestructura.ReputacionRepositorio;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El puntaje de reputacion de alguien, para quien tenga que decidir con el.
 *
 * <p>Lo preguntan CU-64 y CU-68 de {@code grupos}: aceptar a alguien en un grupo o
 * dejarle tomar un cupo depende de su historial, y ese historial es de este servicio.
 *
 * <p><b>Quien todavia no tiene puntaje no tiene cero.</b> Cero seria el peor puntaje
 * posible, y quien recien llega no hizo nada malo: se devuelve {@code SIN_HISTORIAL} y
 * quien pregunta decide que hacer con eso. Confundir «no se sabe» con «malo» le cierra
 * la puerta a todo el que empieza.
 */
@Service
public class ConsultarPuntaje {

    private final Datos datos;
    private final ReputacionRepositorio reputaciones;

    public ConsultarPuntaje(Datos datos, ReputacionRepositorio reputaciones) {
        this.datos = datos;
        this.reputaciones = reputaciones;
    }

    @Transactional(readOnly = true)
    public Puntaje ejecutar(UUID usuarioId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> reputaciones
                .puntajeDe(dsl, usuarioId)
                .map(p -> new Puntaje(true, p.puntaje(), p.nivelConfianza()))
                .orElseGet(() -> new Puntaje(false, BigDecimal.ZERO, "SIN_HISTORIAL")));
    }

    public record Puntaje(boolean tieneHistorial, BigDecimal puntaje, String nivelDeConfianza) {}
}
