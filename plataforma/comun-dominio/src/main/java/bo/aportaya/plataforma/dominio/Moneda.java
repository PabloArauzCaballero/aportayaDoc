package bo.aportaya.plataforma.dominio;

/**
 * Enumeracion cerrada. Sumar BOB con USD falla con un error del dominio, no con una
 * conversion silenciosa: el tipo de cambio es un dato con fecha, no una constante,
 * y convertir sin decir cuando es como redondear sin decir con que regla.
 */
public enum Moneda {
    BOB,
    USD
}
