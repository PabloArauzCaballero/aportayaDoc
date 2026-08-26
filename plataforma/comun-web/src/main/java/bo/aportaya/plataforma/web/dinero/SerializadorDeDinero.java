package bo.aportaya.plataforma.web.dinero;

import bo.aportaya.plataforma.dominio.Dinero;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * {@link Dinero} sale como {@code {"monto": "150.00", "moneda": "BOB"}}: el importe
 * es una CADENA.
 *
 * <p>Si este serializador falta, todo lo demas es decorativo. El cliente es
 * JavaScript y un {@code number} de JSON llega del otro lado como doble: 150.00 se
 * vuelve 150.00000000000001 en cuanto alguien lo suma, y nadie lo nota hasta el
 * cierre diario.
 */
public class SerializadorDeDinero extends JsonSerializer<Dinero> {

    @Override
    public void serialize(Dinero dinero, JsonGenerator generador, SerializerProvider proveedor) throws IOException {
        generador.writeStartObject();
        generador.writeStringField("monto", dinero.toString());
        generador.writeStringField("moneda", dinero.moneda().name());
        generador.writeEndObject();
    }
}
