package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.ExtractoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-15 · Emitir extracto y certificado de saldo.
 *
 * <p>**Si el extracto no cuadra, no se emite.** Un extracto con una cifra que no
 * coincide con el cierre diario es peor que no tener extracto: la persona lo usa para
 * un tramite, se lo rechazan, y descubre el error cuando ya le costo algo. La emision
 * se bloquea y el descuadre queda registrado para que alguien lo mire.
 *
 * <p>El certificado lleva folio y hash para que **un tercero pueda verificarlo sin
 * llamarnos**: un banco que recibe el papel tiene que poder confirmar que es nuestro
 * sin depender de que atendamos el telefono.
 */
@Service
public class CU15EmitirExtracto {

    private final Datos datos;
    private final ExtractoRepositorio extractos;
    private final CuentaBilleteraRepositorio cuentas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU15EmitirExtracto(
            Datos datos,
            ExtractoRepositorio extractos,
            CuentaBilleteraRepositorio cuentas,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.extractos = extractos;
        this.cuentas = cuentas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaExtracto emitir(EntradaExtracto entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var cuenta = cuentas.ver(dsl, entrada.cuentaBilleteraId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(15, 3), "Esa billetera no existe."));

            // AP-CU15-03: solo el titular, o quien tenga permiso explicito sobre la
            // cuenta. El extracto es de los datos mas sensibles que hay.
            if (!cuenta.usuarioId().equals(ctx.usuarioId()) && !entrada.tienePermisoDelegado()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(15, 3), "No hay permiso para emitir el extracto de esa cuenta.");
            }

            // AP-CU15-01.
            var cierreFinal = extractos
                    .cierreDelDia(dsl, cuenta.id(), entrada.hasta())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(15, 1),
                            "No hay cierre diario del " + entrada.hasta() + ": el periodo esta incompleto."));

            Dinero calculado = extractos.saldoCalculadoHasta(dsl, cuenta.id(), entrada.hasta());

            // AP-CU15-02. El cierre diario es la referencia: si el recalculo no le da
            // igual, algo se movio despues de cerrar y hay que averiguar que.
            if (!calculado.equals(cierreFinal.disponible())) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "nucleo_financiero.extracto_descuadrado",
                                "saldo_diario_billetera",
                                cuenta.id(),
                                Map.of(
                                        "fecha", entrada.hasta().toString(),
                                        "segunElCierre",
                                                cierreFinal.disponible().toString(),
                                        "segunElLibro", calculado.toString()),
                                UUID.fromString(ctx.traza().id())));
                // Se DEVUELVE en vez de lanzar, y no es un descuido: lanzar revierte
                // la transaccion y con ella el evento que acaba de registrar el
                // descuadre. El criterio pide las dos cosas —bloquear la emision y
                // dejar constancia—, y una excepcion solo consigue la primera. Quien
                // llama traduce esto a AP-CU15-02 en la frontera HTTP, donde ya no
                // hay nada que revertir.
                return new SalidaExtracto(
                        cuenta.id(),
                        entrada.desde(),
                        entrada.hasta(),
                        cierreFinal.disponible(),
                        0,
                        null,
                        false,
                        "El extracto no cuadra con el cierre del dia: segun el cierre " + cierreFinal.disponible()
                                + " y segun el libro " + calculado + ".");
            }

            int movimientos = extractos.contarMovimientos(dsl, cuenta.id(), entrada.desde(), entrada.hasta());
            String hash = sellar(cuenta.id(), entrada.desde(), entrada.hasta(), calculado, movimientos);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.extracto_emitido",
                            "cuenta_billetera",
                            cuenta.id(),
                            Map.of(
                                    "desde",
                                    entrada.desde().toString(),
                                    "hasta",
                                    entrada.hasta().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaExtracto(
                    cuenta.id(), entrada.desde(), entrada.hasta(), calculado, movimientos, hash, true, null);
        });
    }

    /** El certificado: un papel con folio y hash que un tercero puede verificar. */
    @Transactional
    public SalidaCertificado certificar(EntradaCertificado entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var cuenta = cuentas.ver(dsl, entrada.cuentaBilleteraId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(15, 3), "Esa billetera no existe."));

            var cierre = extractos
                    .cierreDelDia(dsl, cuenta.id(), entrada.fechaCorte())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(15, 1), "No hay cierre del " + entrada.fechaCorte() + "."));

            String folio = extractos.siguienteFolio(dsl, ahora.getYear());
            String hash = sellar(cuenta.id(), entrada.fechaCorte(), entrada.fechaCorte(), cierre.disponible(), 0);

            UUID certificadoId = extractos.emitirCertificado(
                    dsl,
                    cuenta.id(),
                    ctx.usuarioId(),
                    folio,
                    entrada.motivo(),
                    cierre.disponible(),
                    entrada.fechaCorte(),
                    hash,
                    entrada.urlDocumento(),
                    ahora);

            return new SalidaCertificado(certificadoId, folio, hash, cierre.disponible());
        });
    }

    /** Lo que permite a un tercero confirmar el papel sin llamarnos. */
    @Transactional
    public boolean verificar(String folio, String hash, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> extractos.coincideFolioYHash(dsl, folio, hash));
    }

    /**
     * El sello del documento.
     *
     * <p>SHA-256 sobre los datos que no pueden cambiar sin cambiar el hecho. No
     * protege un secreto: sella un contenido, y por eso tiene que poder recomputarlo
     * cualquiera que tenga los mismos datos.
     */
    private String sellar(UUID cuentaId, LocalDate desde, LocalDate hasta, Dinero saldo, int movimientos) {
        String material = String.join(
                "|",
                cuentaId.toString(),
                desde.toString(),
                hasta.toString(),
                saldo.toString(),
                Integer.toString(movimientos));
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder texto = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }

    public record EntradaExtracto(
            UUID cuentaBilleteraId, LocalDate desde, LocalDate hasta, boolean tienePermisoDelegado) {}

    /**
     * @param emitido false cuando el extracto no cuadra: la emision se bloquea pero el
     *     descuadre queda registrado, que es lo que pide el criterio de aceptacion
     * @param motivoDelBloqueo la explicacion, o null si se emitio
     */
    public record SalidaExtracto(
            UUID cuentaBilleteraId,
            LocalDate desde,
            LocalDate hasta,
            Dinero saldoFinal,
            int cantidadMovimientos,
            String hashArchivo,
            boolean emitido,
            String motivoDelBloqueo) {}

    public record EntradaCertificado(
            UUID cuentaBilleteraId, LocalDate fechaCorte, String motivo, String urlDocumento) {}

    public record SalidaCertificado(UUID certificadoId, String folio, String hashDocumento, Dinero saldoCertificado) {}
}
