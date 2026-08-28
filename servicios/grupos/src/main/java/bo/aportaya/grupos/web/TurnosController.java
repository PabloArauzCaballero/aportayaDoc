package bo.aportaya.grupos.web;

import bo.aportaya.grupos.aplicacion.CU62Permutar;
import bo.aportaya.grupos.aplicacion.Consultas;
import bo.aportaya.grupos.dominio.puertos.HechosDeOtrosServicios;
import bo.aportaya.grupos.web.generado.TurnosApi;
import bo.aportaya.grupos.web.generado.modelo.EntradaPermuta;
import bo.aportaya.grupos.web.generado.modelo.SalidaPermuta;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de {@code /turnos}: permutar dos turnos.
 *
 * <p>Si alguna de las dos partes tiene deuda no se permuta, y ese dato es de
 * {@code aportes}: se pregunta **antes** de abrir la transaccion (invariante 6).
 * Adelantar el cobro de un moroso lo termina pagando el grupo entero.
 */
@RestController
public class TurnosController implements TurnosApi {

    private final CU62Permutar cu62;
    private final Consultas consultas;
    private final HechosDeOtrosServicios afuera;
    private final SesionDeLaPeticion sesion;

    public TurnosController(
            CU62Permutar cu62, Consultas consultas, HechosDeOtrosServicios afuera, SesionDeLaPeticion sesion) {
        this.cu62 = cu62;
        this.consultas = consultas;
        this.afuera = afuera;
        this.sesion = sesion;
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaPermuta> solicitarPermuta(UUID idempotencyKey, EntradaPermuta cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-62", cuerpo.getTurnoOrigenId().toString());

        var solicitante = afuera.estadoDePagos(ctx.usuarioId());
        var contraparte = afuera.estadoDePagos(cuerpo.getContraparteId());
        boolean loPermiteElReglamento = consultas.permitePermuta(cuerpo.getTurnoOrigenId(), ctx);

        UUID solicitudId = cu62.solicitar(
                new CU62Permutar.EntradaPermuta(
                        cuerpo.getTurnoOrigenId(),
                        cuerpo.getTurnoDestinoId(),
                        ctx.usuarioId(),
                        cuerpo.getContraparteId(),
                        cuerpo.getMotivo(),
                        solicitante.alDia(),
                        contraparte.alDia(),
                        loPermiteElReglamento),
                ctx);

        var respuesta = new SalidaPermuta();
        respuesta.setSolicitudId(solicitudId);
        respuesta.setEstado(SalidaPermuta.EstadoEnum.PENDIENTE);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
