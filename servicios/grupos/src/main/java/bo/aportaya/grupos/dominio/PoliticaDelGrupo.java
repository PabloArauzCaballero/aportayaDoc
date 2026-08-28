package bo.aportaya.grupos.dominio;

import java.math.BigDecimal;

/**
 * Lo que el grupo le exige a quien quiere entrar, y con cuanto decide.
 *
 * <p>Vive en el dominio y no junto al repositorio que la lee porque la usan la
 * postulacion, el traspaso y el acuerdo, y las tres estan en capas distintas. Un record
 * de infraestructura obligaria a la frontera a importar infraestructura para leerlo, que
 * es lo que {@code ArquitecturaTest} prohibe — y con razon: la forma en que se guarda
 * dejaria de poder cambiar sin tocar la API.
 */
public record PoliticaDelGrupo(String kycMinimo, int reputacionMinima, BigDecimal quorum, int cuposLibres) {}
