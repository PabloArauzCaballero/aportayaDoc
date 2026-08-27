package bo.aportaya.auditoria.web;

import bo.aportaya.auditoria.aplicacion.CU98PublicarTablero;
import bo.aportaya.auditoria.web.generado.IndicadoresApi;
import bo.aportaya.auditoria.web.generado.modelo.Indicador;
import bo.aportaya.auditoria.web.generado.modelo.IndicadorSerieInner;
import bo.aportaya.auditoria.web.generado.modelo.SalidaTablero;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de CU-98: traduce y delega, sin logica.
 *
 * <p>Lo unico que decide es como se serializa un numero, y ahi hay una sola regla que
 * importa: **el valor sale como cadena, con la escala que trae de la base**. Pasarlo a
 * `number` lo convertiria en un doble en el navegador, y el numero del tablero dejaria
 * de ser el numero de la tabla.
 */
@RestController
public class IndicadoresController implements IndicadoresApi {

    private final CU98PublicarTablero cu98;
    private final SesionDeLaPeticion sesion;

    public IndicadoresController(CU98PublicarTablero cu98, SesionDeLaPeticion sesion) {
        this.cu98 = cu98;
        this.sesion = sesion;
    }

    @Override
    @Permiso("INDICADORES_VER")
    public ResponseEntity<SalidaTablero> publicarTablero(
            String periodo, String dimension, UUID dimensionId, Integer periodosDeSerie) {

        Traza.marcarCasoDeUso("CU-98", periodo);

        var salida = cu98.ejecutar(
                new CU98PublicarTablero.EntradaTablero(
                        periodo,
                        dimension,
                        Optional.ofNullable(dimensionId),
                        periodosDeSerie == null ? 6 : periodosDeSerie),
                sesion.actual());

        return ResponseEntity.ok(mapear(salida));
    }

    private static SalidaTablero mapear(CU98PublicarTablero.SalidaTablero salida) {
        SalidaTablero cuerpo = new SalidaTablero(
                salida.periodo(),
                SalidaTablero.DimensionEnum.fromValue(salida.dimension()),
                salida.indicadores().stream().map(IndicadoresController::mapear).toList());
        salida.dimensionId().ifPresent(cuerpo::setDimensionId);
        return cuerpo;
    }

    private static Indicador mapear(CU98PublicarTablero.Indicador indicador) {
        Indicador cuerpo = new Indicador(
                indicador.codigo(),
                indicador.nombre(),
                comoCadena(indicador.valor()),
                indicador.unidad(),
                Indicador.FamiliaEnum.fromValue(indicador.familia()),
                indicador.duenoFamilia(),
                indicador.definicionVersion(),
                indicador.calculadoEn());

        indicador.meta().map(IndicadoresController::comoCadena).ifPresent(cuerpo::setMeta);
        indicador.cumpleMeta().ifPresent(cuerpo::setCumpleMeta);
        indicador
                .variacionPeriodoAnterior()
                .map(IndicadoresController::comoCadena)
                .ifPresent(cuerpo::setVariacionPeriodoAnterior);
        cuerpo.setSerie(mapearSerie(indicador.serie()));
        return cuerpo;
    }

    private static List<IndicadorSerieInner> mapearSerie(List<CU98PublicarTablero.PuntoDeSerie> serie) {
        return serie.stream()
                .map(punto -> new IndicadorSerieInner(punto.periodo(), comoCadena(punto.valor())))
                .toList();
    }

    /**
     * `toPlainString` y no `toString`: para un valor chico o muy grande, `toString`
     * usa notacion cientifica —`1E+3`— y en un tablero eso no se lee, se adivina.
     */
    private static String comoCadena(BigDecimal valor) {
        return valor.toPlainString();
    }
}
