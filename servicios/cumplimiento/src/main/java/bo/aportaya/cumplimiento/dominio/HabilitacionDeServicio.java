package bo.aportaya.cumplimiento.dominio;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/**
 * CU-46 · Decide si un servicio puede ofrecerse. Puro: no toca base ni reloj.
 *
 * <p>El orden importa y no es caprichoso. Primero la licencia; si no alcanza, el
 * entorno de prueba. Al reves, un sandbox vencido podria tapar que la licencia si
 * cubria el servicio, y se rechazaria algo perfectamente autorizado.
 */
public final class HabilitacionDeServicio {

    private HabilitacionDeServicio() {}

    /** Por donde quedo habilitado el servicio, o por que no. */
    public enum Via {
        LICENCIA,
        SANDBOX,
        NINGUNA
    }

    public record LimitesSandbox(int usuarios, BigDecimal montoOperacion) {}

    public record Decision(boolean habilitado, Via via, Optional<LimitesSandbox> limites, Optional<String> motivo) {

        static Decision porLicencia() {
            return new Decision(true, Via.LICENCIA, Optional.empty(), Optional.empty());
        }

        static Decision porSandbox(LimitesSandbox limites) {
            return new Decision(true, Via.SANDBOX, Optional.of(limites), Optional.empty());
        }

        static Decision negada(String motivo) {
            return new Decision(false, Via.NINGUNA, Optional.empty(), Optional.of(motivo));
        }
    }

    /**
     * @param serviciosDeSalida los codigos que siguen permitidos con la licencia
     *     suspendida o revocada. Llega de configuracion, no de una constante: la
     *     boveda no tiene un catalogo de «servicios de salida», y hornearlo en el
     *     codigo seria inventar la lista que este metodo existe para respetar.
     */
    public static Decision resolver(
            String servicio,
            EstadoDeLicencia estado,
            boolean licenciaVigente,
            Set<String> alcanceAutorizado,
            Optional<Sandbox> sandbox,
            Set<String> serviciosDeSalida) {

        if (estado.permiteSoloSalidaDeFondos()) {
            return serviciosDeSalida.contains(servicio)
                    ? Decision.porLicencia()
                    : Decision.negada(
                            "La licencia esta " + estado + ": solo se puede retirar el saldo y cerrar la cuenta.");
        }

        if (estado.habilitaServicioFinanciero() && licenciaVigente && alcanceAutorizado.contains(servicio)) {
            return Decision.porLicencia();
        }

        // El sandbox es la segunda puerta, no un permiso mas debil: R-LIC-01 lo pone
        // al mismo nivel que la licencia con un OR.
        if (sandbox.isPresent()) {
            Sandbox caja = sandbox.get();
            if (!caja.cubre(servicio)) {
                return Decision.negada(motivoFueraDeAlcance(estado, servicio));
            }
            if (caja.cupoAgotado()) {
                return Decision.negada("El entorno de prueba de " + servicio + " llego a su tope de "
                        + caja.limiteUsuarios() + " usuarios.");
            }
            return Decision.porSandbox(new LimitesSandbox(caja.limiteUsuarios(), caja.limiteMontoOperacion()));
        }

        return Decision.negada(motivoFueraDeAlcance(estado, servicio));
    }

    private static String motivoFueraDeAlcance(EstadoDeLicencia estado, String servicio) {
        return estado.habilitaServicioFinanciero()
                ? "El servicio " + servicio + " no esta en el alcance autorizado."
                : "Todavia no hay licencia otorgada: " + servicio + " no puede ofrecerse.";
    }

    /** El entorno de prueba tal como lo describe {@code entorno_prueba_regulado}. */
    public record Sandbox(
            String servicioEnPrueba,
            boolean activoHoy,
            int limiteUsuarios,
            BigDecimal limiteMontoOperacion,
            int usuariosRegistrados) {

        boolean cubre(String servicio) {
            return activoHoy && servicioEnPrueba.equals(servicio);
        }

        boolean cupoAgotado() {
            return usuariosRegistrados >= limiteUsuarios;
        }

        /** R-LIC-02: un monto que supera el tope del sandbox no entra. */
        public boolean admiteMonto(BigDecimal monto) {
            return monto.compareTo(limiteMontoOperacion) <= 0;
        }
    }
}
