package bo.aportaya.tarifas.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.tarifas.infraestructura.TarifarioRepositorio;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cual es el tarifario vigente de un codigo, ahora.
 *
 * <p>La pregunta CU-20 al crear un grupo: **sin tarifario vigente no se abre nada**
 * (R-CON-07), porque el precio es justamente lo que cada participante acepta al firmar
 * el reglamento. Que devuelva vacio es una respuesta valida y significa que no se puede
 * operar, no que hubo un error.
 */
@Service
public class ConsultarTarifarioVigente {

    private final Datos datos;
    private final TarifarioRepositorio tarifarios;
    private final Reloj reloj;

    public ConsultarTarifarioVigente(Datos datos, TarifarioRepositorio tarifarios, Reloj reloj) {
        this.datos = datos;
        this.tarifarios = tarifarios;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> ejecutar(String codigo, ContextoSesion ctx) {
        var ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> tarifarios.vigente(dsl, codigo, ahora));
    }
}
