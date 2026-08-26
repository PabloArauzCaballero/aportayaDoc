package bo.aportaya.grupos.dominio;

import bo.aportaya.plataforma.dominio.Dinero;

/**
 * La pregunta que define todo un retiro: ¿debe o le deben?
 *
 * <p>Atomo puro, y con dinero de verdad. La respuesta depende de una sola cosa —si ya
 * cobro su turno o no—, y todo lo demas se sigue de ahi:
 *
 * <ul>
 *   <li><b>Ya cobro</b>: se llevo la bolsa y le quedan aportes por hacer. Debe al
 *       grupo, y sin plan de pago el retiro no se aprueba. Dejarlo irse seria dejar
 *       que el resto le cubra lo que falta.
 *   <li><b>No cobro</b>: el grupo le debe lo aportado, neto de deuda y recargos. Y se
 *       le paga <b>al cierre del ciclo</b>, no antes: sacar plata de la bolsa a mitad
 *       de camino perjudica a los que todavia no cobraron.
 * </ul>
 */
public record PosicionAlRetirarse(Tipo tipo, Dinero monto, Momento momentoDeLiquidacion) {

    public static PosicionAlRetirarse calcular(
            boolean yaCobroSuTurno,
            boolean elGrupoArranco,
            Dinero totalAportado,
            Dinero deudaVigente,
            Dinero aportesRestantesDelCiclo) {

        // El grupo que no arranco no tiene nada que liquidar: se devuelve integro.
        if (!elGrupoArranco) {
            return new PosicionAlRetirarse(Tipo.ACREEDORA, totalAportado, Momento.INMEDIATO);
        }

        if (yaCobroSuTurno) {
            Dinero debe = aportesRestantesDelCiclo.mas(deudaVigente);
            return debe.esCero()
                    ? new PosicionAlRetirarse(Tipo.NEUTRA, debe, Momento.INMEDIATO)
                    : new PosicionAlRetirarse(Tipo.DEUDORA, debe, Momento.CON_PLAN_DE_PAGO);
        }

        Dinero aFavor = totalAportado.menos(deudaVigente);
        if (aFavor.esNegativo()) {
            return new PosicionAlRetirarse(Tipo.DEUDORA, aFavor.negado(), Momento.CON_PLAN_DE_PAGO);
        }
        return aFavor.esCero()
                ? new PosicionAlRetirarse(Tipo.NEUTRA, aFavor, Momento.INMEDIATO)
                : new PosicionAlRetirarse(Tipo.ACREEDORA, aFavor, Momento.AL_CIERRE_DEL_CICLO);
    }

    /** Sin plan de pago aceptado, un retiro deudor no se aprueba. */
    public boolean exigePlanDePago() {
        return tipo == Tipo.DEUDORA;
    }

    public enum Tipo {
        ACREEDORA,
        DEUDORA,
        NEUTRA
    }

    public enum Momento {
        INMEDIATO,
        AL_CIERRE_DEL_CICLO,
        CON_PLAN_DE_PAGO
    }
}
