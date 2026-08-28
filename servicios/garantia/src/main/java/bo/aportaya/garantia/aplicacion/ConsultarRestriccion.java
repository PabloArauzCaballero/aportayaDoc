package bo.aportaya.garantia.aplicacion;

import bo.aportaya.garantia.infraestructura.GestionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si alguien esta en la lista de restriccion interna, y con que nivel.
 *
 * <p>La pregunta CU-68 antes de aceptar a alguien en un grupo. La lista es de este
 * servicio y {@code grupos} no la puede leer (invariante 11).
 *
 * <p>Devuelve tambien **cuanto tendria que pagar para salir**: una restriccion sin
 * salida es una condena, y el que consulta necesita poder decirle a la persona que
 * hacer, no solo que no puede entrar.
 */
@Service
public class ConsultarRestriccion {

    private final Datos datos;
    private final GestionRepositorio gestion;
    private final Reloj reloj;

    public ConsultarRestriccion(Datos datos, GestionRepositorio gestion, Reloj reloj) {
        this.datos = datos;
        this.gestion = gestion;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Restriccion ejecutar(UUID usuarioId, ContextoSesion ctx) {
        var ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> gestion.restriccionVigente(dsl, usuarioId, ahora)
                .map(r -> new Restriccion(true, r.nivel(), gestion.deudaViva(dsl, usuarioId)))
                .orElseGet(() -> new Restriccion(false, null, BigDecimal.ZERO)));
    }

    public record Restriccion(boolean vigente, String nivel, BigDecimal montoQueLaLevanta) {}
}
