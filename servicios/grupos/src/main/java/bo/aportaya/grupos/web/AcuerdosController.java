package bo.aportaya.grupos.web;

import bo.aportaya.grupos.aplicacion.CU63Acordar;
import bo.aportaya.grupos.aplicacion.Consultas;
import bo.aportaya.grupos.web.generado.AcuerdosApi;
import bo.aportaya.grupos.web.generado.modelo.EntradaAcuerdo;
import bo.aportaya.grupos.web.generado.modelo.EntradaVoto;
import bo.aportaya.grupos.web.generado.modelo.SalidaAcuerdo;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /acuerdos}: proponer y votar.
 *
 * <p>El quorum no lo elige quien propone: sale del reglamento del grupo. Si lo pusiera
 * el proponente, cualquiera podria abrir una votacion con el quorum que le convenga y
 * el acuerdo colectivo dejaria de serlo.
 */
@RestController
public class AcuerdosController implements AcuerdosApi {

    private final CU63Acordar cu63;
    private final Consultas consultas;
    private final SesionDeLaPeticion sesion;
    private final Reloj reloj;

    public AcuerdosController(CU63Acordar cu63, Consultas consultas, SesionDeLaPeticion sesion, Reloj reloj) {
        this.cu63 = cu63;
        this.consultas = consultas;
        this.sesion = sesion;
        this.reloj = reloj;
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaAcuerdo> proponerAcuerdo(UUID idempotencyKey, EntradaAcuerdo cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-63", cuerpo.getTipo().getValue());

        var politica = consultas
                .politicaDelGrupo(cuerpo.getGrupoId(), ctx)
                .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(63, 1), "Ese grupo no existe."));

        UUID acuerdoId = cu63.proponer(
                new CU63Acordar.EntradaPropuesta(
                        cuerpo.getGrupoId(),
                        cuerpo.getTipo().getValue(),
                        cuerpo.getDescripcion(),
                        ctx.usuarioId(),
                        politica.quorum(),
                        Optional.ofNullable(cuerpo.getReferenciaAfectadaId()),
                        reloj.ahora().atOffset(ZoneOffset.UTC).plusDays(cuerpo.getDiasVotacion())),
                ctx);

        var respuesta = new SalidaAcuerdo();
        respuesta.setAcuerdoId(acuerdoId);
        respuesta.setEstado("EN_VOTACION");
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaAcuerdo> votarAcuerdo(UUID acuerdoId, UUID idempotencyKey, EntradaVoto cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-63", acuerdoId.toString());

        cu63.votar(acuerdoId, ctx.usuarioId(), cuerpo.getVoto().getValue(), ctx);

        var respuesta = new SalidaAcuerdo();
        respuesta.setAcuerdoId(acuerdoId);
        respuesta.setEstado(cu63.resolver(acuerdoId, ctx));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
