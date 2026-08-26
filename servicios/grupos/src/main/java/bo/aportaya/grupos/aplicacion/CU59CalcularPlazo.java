package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.AlcanceDeCalendario;
import bo.aportaya.grupos.dominio.CalendarioVacio;
import bo.aportaya.grupos.infraestructura.CalendarioRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CalendarioHabil;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.PlazoHabil;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-59 · Calcular una fecha límite en días hábiles.
 *
 * <p>Devuelve tambien **que dias se saltearon**, porque un plazo que no se puede
 * explicar no se puede defender: ni ante el cliente que pregunta por que su fecha es
 * esa, ni ante el supervisor que pregunta lo mismo seis meses despues.
 *
 * <p>El resultado se guarda donde se use, y **no se recalcula**. Si se declara un
 * feriado despues, ese plazo no se mueve: mover un plazo guardado seria reescribir el
 * pasado, y para el cliente seria peor — le cambiamos la fecha que ya le habiamos dicho.
 */
@Service
public class CU59CalcularPlazo {

    private final Datos datos;
    private final CalendarioRepositorio calendario;

    public CU59CalcularPlazo(Datos datos, CalendarioRepositorio calendario) {
        this.datos = datos;
        this.calendario = calendario;
    }

    @Transactional
    public SalidaPlazo ejecutar(EntradaPlazo entrada, ContextoSesion ctx) {
        AlcanceDeCalendario alcance = AlcanceDeCalendario.valueOf(entrada.alcance());
        if (!alcance.completoCon(entrada.referenciaId())) {
            throw new ErrorDeNegocio(
                    CodigoError.de(59, 2), "Un feriado de grupo necesita decir de que grupo se trata.");
        }

        return datos.conContexto(ctx, dsl -> {
            if (!calendario.hayCalendarioPara(dsl, entrada.desde().getYear())) {
                throw new CalendarioVacio(entrada.desde().getYear());
            }

            // Se pide una ventana generosa: con el peor calendario imaginable, `dias`
            // habiles nunca caen mas alla del triple en dias corridos.
            LocalDate hasta = entrada.desde().plusDays(entrada.dias() * 3L + 14);
            Map<LocalDate, String> noHabiles =
                    calendario.noHabilesEntre(dsl, entrada.desde(), hasta, alcance, entrada.referenciaId());

            CalendarioHabil habil = fecha -> noHabiles.containsKey(fecha)
                    || fecha.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || fecha.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;

            LocalDate limite = PlazoHabil.sumar(entrada.desde(), entrada.dias(), habil);
            List<DiaSalteado> salteados = noHabiles.entrySet().stream()
                    .filter(dia -> !dia.getKey().isBefore(entrada.desde())
                            && !dia.getKey().isAfter(limite))
                    .map(dia -> new DiaSalteado(dia.getKey(), dia.getValue()))
                    .toList();

            return new SalidaPlazo(limite, salteados);
        });
    }

    /** Corre al siguiente habil. A favor del cliente: nunca al anterior. */
    @Transactional
    public LocalDate correrSiCae(LocalDate vencimiento, AlcanceDeCalendario alcance, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            Map<LocalDate, String> noHabiles =
                    calendario.noHabilesEntre(dsl, vencimiento, vencimiento.plusDays(30), alcance, Optional.empty());
            return PlazoHabil.siguienteHabil(vencimiento, fecha -> noHabiles.containsKey(fecha));
        });
    }

    public record EntradaPlazo(LocalDate desde, int dias, String alcance, Optional<UUID> referenciaId) {}

    public record SalidaPlazo(LocalDate fechaLimite, List<DiaSalteado> diasSalteados) {}

    public record DiaSalteado(LocalDate fecha, String descripcion) {}
}
