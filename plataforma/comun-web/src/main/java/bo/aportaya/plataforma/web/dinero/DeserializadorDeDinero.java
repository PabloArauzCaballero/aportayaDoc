package bo.aportaya.plataforma.web.dinero;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import bo.aportaya.plataforma.dominio.Moneda;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * Entra con la misma forma con la que sale, y solo con esa.
 *
 * <p>Un numero JSON se rechaza a proposito: aceptarlo seria admitir un doble en la
 * frontera y perder la precision antes de que el dominio la pueda defender.
 */
public class DeserializadorDeDinero extends JsonDeserializer<Dinero> {

    @Override
    public Dinero deserialize(JsonParser analizador, DeserializationContext contexto) throws IOException {
        JsonNode nodo = analizador.readValueAsTree();
        JsonNode monto = nodo.get("monto");
        JsonNode moneda = nodo.get("moneda");
        if (monto == null || moneda == null) {
            throw new ErrorDeDominio("Un importe se escribe {\"monto\": \"150.00\", \"moneda\": \"BOB\"}");
        }
        if (!monto.isTextual()) {
            throw new ErrorDeDominio("El monto viaja como CADENA decimal, no como numero: un number JSON es un doble");
        }
        return Dinero.de(monto.asText(), Moneda.valueOf(moneda.asText()));
    }
}
