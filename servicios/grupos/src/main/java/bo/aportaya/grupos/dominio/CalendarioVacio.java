package bo.aportaya.grupos.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;

/**
 * Se pidio calcular un plazo habil en un periodo sin calendario cargado.
 *
 * <p>**Se rechaza en vez de contar corridos por omision.** Contar corridos daria un
 * numero, y un numero equivocado es peor que ninguno: nadie revisa un plazo que ya
 * tiene fecha. Un diciembre sin feriados cargados es un error de operacion, no un
 * año sin feriados.
 */
public class CalendarioVacio extends ErrorDeNegocio {

    private static final long serialVersionUID = 1L;

    public CalendarioVacio(int anio) {
        super(
                CodigoError.de(59, 5),
                "No hay calendario cargado para %d, asi que no podemos calcular ese plazo.".formatted(anio));
    }
}
