package bo.aportaya.cumplimiento.dominio;

/**
 * Los cinco estados que admite {@code ck_licencia_regulatoria_estado}.
 *
 * <p>El enum no agrega estados ni los reinterpreta: si la base acepta cinco, aca hay
 * cinco. Un sexto estado inventado seria una fila que nunca podria existir.
 */
public enum EstadoDeLicencia {
    EN_TRAMITE,
    CONDICIONADA,
    OTORGADA,
    SUSPENDIDA,
    REVOCADA;

    /** Solo {@code OTORGADA} habilita servicios financieros (CU-46, flujo 1a). */
    public boolean habilitaServicioFinanciero() {
        return this == OTORGADA;
    }

    /**
     * Suspendida y revocada **no** cierran la puerta de salida: la persona sigue
     * pudiendo retirar su plata y cerrar su cuenta (CU-46, flujo 1b). Bloquear la
     * salida convertiria una sancion a la empresa en un secuestro del saldo ajeno.
     */
    public boolean permiteSoloSalidaDeFondos() {
        return this == SUSPENDIDA || this == REVOCADA;
    }
}
