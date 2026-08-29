package bo.aportaya.aportes.aplicacion;

import bo.aportaya.aportes.dominio.EstadoDePagos;
import bo.aportaya.aportes.infraestructura.EstadoDelParticipanteRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que este servicio le contesta a los otros sobre un participante.
 *
 * <p>No es un caso de uso de la boveda: es la consulta que hace posible que
 * {@code grupos} y {@code nucleo-financiero} decidan sin leer este esquema. Sin ella,
 * cada uno tendria que mirar {@code aportes.obligacion_aporte} —el invariante 11— o
 * recibir el dato del cliente, que es peor: el que pide seria quien afirma estar al dia.
 *
 * <p>Solo lee, y aun asi abre transaccion: el contexto viaja con {@code SET LOCAL}
 * dentro de ella, y sin transaccion la politica de fila no aplica.
 */
@Service
public class ConsultarEstadoDelParticipante {

    private final Datos datos;
    private final EstadoDelParticipanteRepositorio estados;

    public ConsultarEstadoDelParticipante(Datos datos, EstadoDelParticipanteRepositorio estados) {
        this.datos = datos;
        this.estados = estados;
    }

    @Transactional(readOnly = true)
    public EstadoDePagos ejecutar(UUID participanteId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> estados.de(dsl, participanteId));
    }

    @Transactional(readOnly = true)
    public int morososDelGrupo(UUID grupoId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> estados.morososDelGrupo(dsl, grupoId));
    }
}
