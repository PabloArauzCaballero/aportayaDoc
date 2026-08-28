package bo.aportaya.notificaciones.aplicacion;

import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.notificaciones.infraestructura.SupresionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si un destino pidio no recibir mas.
 *
 * <p>La pregunta CU-69 de {@code grupos} antes de invitar a alguien: **a quien se dio
 * de baja no se le escribe, aunque quien invita no lo sepa**. La lista de supresion es
 * de este servicio y nadie mas la puede leer (invariante 11).
 *
 * <p>Se pregunta por los tres canales encendidos y alcanza con que uno este suprimido
 * para responder que si: quien pidio no recibir por el canal que usariamos, pidio no
 * recibir.
 */
@Service
public class ConsultarSupresion {

    private final Datos datos;
    private final SupresionRepositorio supresiones;

    public ConsultarSupresion(Datos datos, SupresionRepositorio supresiones) {
        this.datos = datos;
        this.supresiones = supresiones;
    }

    @Transactional(readOnly = true)
    public boolean estaSuprimido(String identificador, String categoria, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> Canal.encendidosPorOmision().stream()
                .anyMatch(canal -> supresiones.estaSuprimido(dsl, identificador, canal, categoria)));
    }
}
