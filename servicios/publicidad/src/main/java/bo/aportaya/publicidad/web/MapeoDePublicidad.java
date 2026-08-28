package bo.aportaya.publicidad.web;

import bo.aportaya.publicidad.aplicacion.CU110AltaDeAnunciante;
import bo.aportaya.publicidad.aplicacion.CU111CrearCampana;
import bo.aportaya.publicidad.aplicacion.CU112ModerarPieza;
import bo.aportaya.publicidad.aplicacion.CU114LiquidarPublicidad;
import bo.aportaya.publicidad.web.generado.modelo.EntradaAnunciante;
import bo.aportaya.publicidad.web.generado.modelo.EntradaCampana;
import bo.aportaya.publicidad.web.generado.modelo.EntradaLiquidacion;
import bo.aportaya.publicidad.web.generado.modelo.EntradaPieza;
import bo.aportaya.publicidad.web.generado.modelo.EntradaSocio;
import bo.aportaya.publicidad.web.generado.modelo.SalidaCampana;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * La traduccion entre los modelos del contrato y los del caso de uso.
 *
 * <p>Vive aparte del controlador porque el generador agrupa las catorce operaciones de
 * {@code /publicidad} en una sola interfaz: el controlador no se puede partir en varios
 * {@code @RestController} sin que Spring registre dos veces cada mapeo. Lo que si se
 * puede separar es esto.
 *
 * <p><b>Dos escalas de importe, y no es un descuido.</b> Un presupuesto es dinero y va
 * al centavo; el costo de una impresion son fracciones de centavo y va con cuatro
 * decimales, porque redondear cada impresion y despues sumar convierte un error de
 * milesimas en uno de bolivianos sobre millones de entregas.
 */
final class MapeoDePublicidad {

    private static final int CENTAVOS = 2;
    private static final int DECIMALES_DEL_COSTO = 4;

    private MapeoDePublicidad() {}

    /** Dinero al centavo, como cadena. Un {@code number} de JSON es un doble. */
    static String monto(BigDecimal valor) {
        return valor.setScale(CENTAVOS, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** El costo de una impresion o un clic, con sus cuatro decimales. */
    static String costo(BigDecimal valor) {
        return valor.setScale(DECIMALES_DEL_COSTO, RoundingMode.UNNECESSARY).toPlainString();
    }

    static CU110AltaDeAnunciante.EntradaSocio socio(EntradaSocio cuerpo) {
        return new CU110AltaDeAnunciante.EntradaSocio(
                cuerpo.getRazonSocial(), cuerpo.getNumeroDocumento(), cuerpo.getRubro(), cuerpo.getEmailContacto());
    }

    static CU110AltaDeAnunciante.EntradaAnunciante anunciante(EntradaAnunciante cuerpo) {
        return new CU110AltaDeAnunciante.EntradaAnunciante(
                cuerpo.getTipo().getValue(),
                cuerpo.getOrganizadorId(),
                cuerpo.getSocioComercialId(),
                cuerpo.getRazonSocialFacturacion(),
                cuerpo.getLimiteGastoMensual() == null ? null : new BigDecimal(cuerpo.getLimiteGastoMensual()),
                cuerpo.getMoneda().getValue());
    }

    static CU111CrearCampana.Entrada campana(EntradaCampana cuerpo) {
        return new CU111CrearCampana.Entrada(
                cuerpo.getCuentaPublicitariaId(),
                cuerpo.getNombre(),
                cuerpo.getObjetivo().getValue(),
                new BigDecimal(cuerpo.getPresupuestoTotal()),
                cuerpo.getMoneda().getValue(),
                cuerpo.getFechaInicio(),
                cuerpo.getFechaFin(),
                cuerpo.getConjuntos().stream()
                        .map(c -> new CU111CrearCampana.Conjunto(
                                c.getSegmentoAudienciaId(),
                                c.getEspacioPublicitarioId(),
                                c.getNombre(),
                                new BigDecimal(c.getPresupuestoDiario()),
                                new BigDecimal(c.getPujaMaxima()),
                                c.getModeloPuja().getValue()))
                        .toList());
    }

    static CU112ModerarPieza.EntradaPieza pieza(EntradaPieza cuerpo) {
        return new CU112ModerarPieza.EntradaPieza(
                cuerpo.getAnuncianteId(),
                cuerpo.getTitulo(),
                cuerpo.getTexto(),
                cuerpo.getUrlRecurso(),
                cuerpo.getTipoRecurso().getValue());
    }

    static CU114LiquidarPublicidad.Entrada liquidacion(UUID cuentaId, EntradaLiquidacion cuerpo) {
        return new CU114LiquidarPublicidad.Entrada(
                cuentaId, cuerpo.getPeriodo(), cuerpo.getFacturaElectronicaId(), cuerpo.getCuentaPorCobrarId());
    }

    static SalidaCampana campana(CU111CrearCampana.Salida salida) {
        var respuesta = new SalidaCampana();
        respuesta.setCampanaPublicitariaId(salida.campanaPublicitariaId());
        respuesta.setEstado(SalidaCampana.EstadoEnum.fromValue(salida.estado()));
        respuesta.setPresupuestoTotal(monto(salida.presupuestoTotal()));
        return respuesta;
    }
}
