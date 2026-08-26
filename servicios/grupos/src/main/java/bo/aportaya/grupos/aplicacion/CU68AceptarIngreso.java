package bo.aportaya.grupos.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-68 · Postular a un grupo y ser emparejado
 *
 * Generado por scripts/nuevo_cu.py desde docs/CasosDeUso/. La especificacion
 * manda: si algo de acá no coincide con el caso de uso, el error está acá.
 *
 * ANTES DE ESCRIBIR EL CUERPO, respondé por escrito las seis preguntas de
 * frontera transaccional (skill `frontera-transaccional`) y esperá el visto bueno.
 */
@Service
public class CU68AceptarIngreso {

    // Las moléculas entran por constructor. Nada de inyección en campos: esconde
    // la dependencia y hace imposible construir la clase en una prueba unitaria.

    @Transactional // la ÚNICA frontera transaccional del caso
    public Object ejecutar(Object entrada, Object ctx) {
        // return datos.conContexto(ctx, dsl -> {        ← SET LOCAL, misma conexión
        //     idempotencia.exigirNueva(dsl, entrada.clave());   ← ANTES de escribir
        //     ...                                               ← átomos puros
        //     outbox.emitir(dsl, "<modulo>.<evento>", carga);   ← misma transacción
        //     return ...;
        // });
        throw new UnsupportedOperationException("CU-68 sin implementar");
    }
}
