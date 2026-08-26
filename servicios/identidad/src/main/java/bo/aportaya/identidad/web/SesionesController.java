package bo.aportaya.identidad.web;

import bo.aportaya.identidad.aplicacion.CU04Autenticar;
import bo.aportaya.identidad.aplicacion.EntradaAutenticacion;
import bo.aportaya.identidad.dominio.PoliticaDeIntentos;
import bo.aportaya.identidad.dominio.ResultadoDeAutenticacion;
import bo.aportaya.identidad.web.generado.SesionesApi;
import bo.aportaya.identidad.web.generado.modelo.SalidaAutenticacion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.web.seguridad.Publico;
import bo.aportaya.plataforma.web.traza.Traza;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina traduce y delega. Sin {@code if} de negocio, sin calculos, sin SQL y sin
 * transaccion.
 *
 * <p>Lo unico que decide aca es el codigo HTTP, y a partir de un resultado que el
 * organismo ya cerro: convertir el rechazo en {@code 422} DESPUES del {@code COMMIT}
 * es lo que deja escrito el intento fallido.
 */
@RestController
public class SesionesController implements SesionesApi {

    private final CU04Autenticar cu04;
    private final PoliticaDeIntentos politica;
    private final Duration vigenciaDeSesion;
    private final HttpServletRequest peticion;

    public SesionesController(
            CU04Autenticar cu04,
            HttpServletRequest peticion,
            @Value("${aportaya.acceso.intentos-maximos}") int intentosMaximos,
            @Value("${aportaya.acceso.duracion-bloqueo}") Duration duracionDelBloqueo,
            @Value("${aportaya.acceso.vigencia-sesion}") Duration vigenciaDeSesion) {
        this.cu04 = cu04;
        this.peticion = peticion;
        this.politica = new PoliticaDeIntentos(intentosMaximos, duracionDelBloqueo);
        this.vigenciaDeSesion = vigenciaDeSesion;
    }

    @Override
    @Publico("CU-04: el ingreso es el momento en que todavia no hay sesion")
    public ResponseEntity<SalidaAutenticacion> autenticar(
            bo.aportaya.identidad.web.generado.modelo.EntradaAutenticacion cuerpo) {
        Traza.marcarCasoDeUso("CU-04", cuerpo.getTelefonoE164());

        ResultadoDeAutenticacion resultado = cu04.ejecutar(mapear(cuerpo), politica);

        if (!resultado.exitoso()) {
            throw new ErrorDeNegocio(resultado.codigo().orElseThrow(), resultado.mensaje());
        }
        return ResponseEntity.ok(mapear(resultado));
    }

    private EntradaAutenticacion mapear(bo.aportaya.identidad.web.generado.modelo.EntradaAutenticacion cuerpo) {
        return new EntradaAutenticacion(
                cuerpo.getTelefonoE164(),
                cuerpo.getCredencial() != null ? cuerpo.getCredencial().toCharArray() : new char[0],
                cuerpo.getHuellaDispositivo(),
                cuerpo.getPlataforma() != null ? cuerpo.getPlataforma().getValue() : "ANDROID",
                Optional.ofNullable(peticion.getRemoteAddr()).orElse("0.0.0.0"),
                Optional.ofNullable(peticion.getHeader("User-Agent")).orElse("desconocido"),
                Traza.actual(),
                Optional.ofNullable(cuerpo.getFactor())
                        .map(bo.aportaya.identidad.web.generado.modelo.FactorPresentado::getValor),
                false,
                vigenciaDeSesion);
    }

    private SalidaAutenticacion mapear(ResultadoDeAutenticacion resultado) {
        SalidaAutenticacion salida =
                new SalidaAutenticacion(resultado.requiereFactorAdicional(), resultado.dispositivoConfiable());
        resultado.sesionId().ifPresent(salida::setSesionId);
        resultado.expiraEn().ifPresent(salida::setExpiraEn);
        return salida;
    }
}
