package bo.aportaya.grupos.web;

import bo.aportaya.grupos.aplicacion.CU20CrearGrupo;
import bo.aportaya.grupos.aplicacion.CU59CalcularPlazo;
import bo.aportaya.grupos.aplicacion.CU64TraspasarCupo;
import bo.aportaya.grupos.aplicacion.CU65Retirarse;
import bo.aportaya.grupos.aplicacion.CU68Postular;
import bo.aportaya.grupos.aplicacion.CU69Invitar;
import bo.aportaya.grupos.aplicacion.Consultas;
import bo.aportaya.grupos.dominio.puertos.HechosDeOtrosServicios;
import bo.aportaya.grupos.web.generado.GruposApi;
import bo.aportaya.grupos.web.generado.modelo.ActividadEnGrupos;
import bo.aportaya.grupos.web.generado.modelo.AliasResuelto;
import bo.aportaya.grupos.web.generado.modelo.CompromisoDeSorteo;
import bo.aportaya.grupos.web.generado.modelo.EntradaCompromiso;
import bo.aportaya.grupos.web.generado.modelo.EntradaGrupo;
import bo.aportaya.grupos.web.generado.modelo.EntradaInvitacion;
import bo.aportaya.grupos.web.generado.modelo.EntradaPostulacion;
import bo.aportaya.grupos.web.generado.modelo.EntradaRetiro;
import bo.aportaya.grupos.web.generado.modelo.EntradaRevelacion;
import bo.aportaya.grupos.web.generado.modelo.EntradaTraspaso;
import bo.aportaya.grupos.web.generado.modelo.PaqueteDelSorteo;
import bo.aportaya.grupos.web.generado.modelo.RevelacionDeSorteo;
import bo.aportaya.grupos.web.generado.modelo.SalidaGrupo;
import bo.aportaya.grupos.web.generado.modelo.SalidaInvitacion;
import bo.aportaya.grupos.web.generado.modelo.SalidaPlazoHabil;
import bo.aportaya.grupos.web.generado.modelo.SalidaPostulacion;
import bo.aportaya.grupos.web.generado.modelo.SalidaRetiro;
import bo.aportaya.grupos.web.generado.modelo.SalidaTraspaso;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /grupos}.
 *
 * <p><b>Aca se junta media plataforma.</b> Un pasanaku se decide con hechos de otros
 * seis servicios —si el organizador esta habilitado, si hay tarifario, si la licencia
 * cubre, si quien pide esta al dia, cuanta reputacion tiene, si esta restringido— y
 * ninguno vive en este esquema. Todos se preguntan por contrato (invariante 11) y
 * **antes** de abrir la transaccion (invariante 6).
 *
 * <p>Las tres ultimas operaciones no son casos de uso: son lo que este servicio le
 * contesta a los demas para que ellos tampoco tengan que leer su esquema.
 */
@RestController
public class GruposController implements GruposApi {

    private final CU20CrearGrupo cu20;
    private final CU59CalcularPlazo cu59;
    private final CU64TraspasarCupo cu64;
    private final CU65Retirarse cu65;
    private final CU68Postular cu68;
    private final CU69Invitar cu69;
    private final Consultas consultas;
    private final HechosDeOtrosServicios afuera;
    private final SesionDeLaPeticion sesion;
    private final String codigoTarifario;
    private final String servicioDeLicencia;
    private final int topeDeReenvios;
    private final java.math.BigDecimal afinidadNeutra;
    private final RespuestasAOtrosServicios respuestas;
    private final SorteoDelGrupo sorteo;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public GruposController(
            CU20CrearGrupo cu20,
            CU59CalcularPlazo cu59,
            CU64TraspasarCupo cu64,
            CU65Retirarse cu65,
            CU68Postular cu68,
            CU69Invitar cu69,
            Consultas consultas,
            HechosDeOtrosServicios afuera,
            SesionDeLaPeticion sesion,
            @Value("${aportaya.tarifas.codigo-tarifario}") String codigoTarifario,
            @Value("${aportaya.grupo.servicio-de-licencia}") String servicioDeLicencia,
            @Value("${aportaya.grupo.tope-de-reenvios-de-invitacion}") int topeDeReenvios,
            @Value("${aportaya.grupo.afinidad-neutra}") java.math.BigDecimal afinidadNeutra,
            RespuestasAOtrosServicios respuestas,
            SorteoDelGrupo sorteo) {
        this.cu20 = cu20;
        this.cu59 = cu59;
        this.cu64 = cu64;
        this.cu65 = cu65;
        this.cu68 = cu68;
        this.cu69 = cu69;
        this.consultas = consultas;
        this.afuera = afuera;
        this.sesion = sesion;
        this.codigoTarifario = codigoTarifario;
        this.servicioDeLicencia = servicioDeLicencia;
        this.topeDeReenvios = topeDeReenvios;
        this.afinidadNeutra = afinidadNeutra;
        this.respuestas = respuestas;
        this.sorteo = sorteo;
    }

    @Override
    @Permiso("GRUPO_CREAR")
    public ResponseEntity<SalidaGrupo> crearGrupo(UUID idempotencyKey, EntradaGrupo cuerpo) {
        Traza.marcarCasoDeUso("CU-20", cuerpo.getNombre());

        var salida = cu20.ejecutar(
                MapeoDeAltaDeGrupo.entrada(cuerpo, afuera, codigoTarifario, servicioDeLicencia), sesion.actual());

        var respuesta = new SalidaGrupo();
        respuesta.setGrupoId(salida.grupoId());
        respuesta.setFondoPorPeriodo(salida.fondoPorPeriodo());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta);
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaPlazoHabil> calcularPlazoHabil(
            LocalDate desde, Integer dias, String alcance, UUID referenciaId) {
        Traza.marcarCasoDeUso("CU-59", alcance);

        var salida = cu59.ejecutar(
                new CU59CalcularPlazo.EntradaPlazo(desde, dias, alcance, Optional.ofNullable(referenciaId)),
                sesion.actual());

        return ResponseEntity.ok(MapeoDeGrupos.plazo(salida));
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<CompromisoDeSorteo> comprometerSorteo(
            UUID grupoId, UUID idempotencyKey, EntradaCompromiso cuerpo) {
        Traza.marcarCasoDeUso("CU-60", grupoId.toString());
        return sorteo.comprometer(grupoId, cuerpo, sesion.actual());
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<RevelacionDeSorteo> revelarSorteo(
            UUID grupoId, UUID idempotencyKey, EntradaRevelacion cuerpo) {
        Traza.marcarCasoDeUso("CU-60", grupoId.toString());
        return sorteo.revelar(grupoId, cuerpo, sesion.actual());
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaRetiro> solicitarRetiro(UUID grupoId, UUID idempotencyKey, EntradaRetiro cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-65", cuerpo.getParticipanteId().toString());

        var salida = cu65.solicitar(
                MapeoDeGrupos.entradaDeRetiro(
                        cuerpo,
                        afuera.estadoDePagos(cuerpo.getParticipanteId()),
                        consultas.yaCobroSuTurno(cuerpo.getParticipanteId(), ctx)),
                ctx);

        var respuesta = new SalidaRetiro();
        respuesta.setSolicitudId(salida.solicitudId());
        respuesta.setPosicion(
                SalidaRetiro.PosicionEnum.fromValue(salida.posicion().tipo().name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaTraspaso> traspasarCupo(
            UUID grupoId, UUID cupoId, UUID idempotencyKey, EntradaTraspaso cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-64", cupoId.toString());

        var politica = consultas
                .politicaDelGrupo(grupoId, ctx)
                .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(64, 1), "Ese grupo no existe."));
        var entrante = afuera.reputacion(cuerpo.getUsuarioEntranteId());
        var saliente = consultas
                .participanteDe(grupoId, ctx.usuarioId(), ctx)
                .map(afuera::estadoDePagos)
                .orElseGet(() -> afuera.estadoDePagos(ctx.usuarioId()));

        UUID traspasoId = cu64.ejecutar(
                new CU64TraspasarCupo.EntradaTraspaso(
                        cupoId,
                        cuerpo.getUsuarioEntranteId(),
                        cuerpo.getMotivo(),
                        saliente.alDia(),
                        // El nivel de diligencia del entrante lo decide cumplimiento al
                        // aceptar la postulacion; aca alcanza con el minimo del grupo.
                        politica.kycMinimo(),
                        politica.kycMinimo(),
                        entrante.puntaje().intValue(),
                        politica.reputacionMinima(),
                        cuerpo.getAcuerdoId() != null,
                        true,
                        Optional.ofNullable(cuerpo.getAcuerdoId())),
                ctx);

        var respuesta = new SalidaTraspaso();
        respuesta.setTraspasoId(traspasoId);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaPostulacion> postularAlGrupo(
            UUID grupoId, UUID idempotencyKey, EntradaPostulacion cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-68", grupoId.toString());

        var salida =
                cu68.postular(MapeoDePostulacion.entrada(grupoId, cuerpo, consultas, afuera, afinidadNeutra, ctx), ctx);

        var respuesta = new SalidaPostulacion();
        respuesta.setSolicitudId(salida.solicitudId());
        respuesta.setPuntaje(salida.puntaje().toPlainString());
        respuesta.setMotivos(salida.motivos());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * La invitacion.
     *
     * <p>Si el destinatario esta suprimido no se envia nada **y se responde como si
     * hubiera salido bien**: decir «esa persona pidio no recibir mensajes» ya cuenta
     * algo de ella a quien no tiene por que saberlo.
     */
    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaInvitacion> invitarAlGrupo(
            UUID grupoId, UUID idempotencyKey, EntradaInvitacion cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-69", grupoId.toString());

        String telefono = cuerpo.getTelefonoInvitado();
        boolean suprimido = afuera.contactoSuprimido(telefono, "INVITACION_GRUPO");
        boolean yaEsta = afuera.usuarioDelTelefono(telefono)
                .map(usuario -> consultas.yaEsParticipante(grupoId, usuario, ctx))
                .orElse(false);

        var salida = cu69.invitar(
                new CU69Invitar.EntradaInvitacion(
                        grupoId,
                        telefono,
                        cuerpo.getNombreSugerido(),
                        cuerpo.getCanal().getValue(),
                        suprimido,
                        yaEsta,
                        topeDeReenvios,
                        // El token se pide solo si hay algo que enviar: pedirlo para una
                        // invitacion que no sale seria emitir un enlace vivo sin destino.
                        suprimido || yaEsta
                                ? null
                                : afuera.tokenDeInvitacion(
                                        cuerpo.getCanal().getValue(), MapeoDeGrupos.enmascarar(telefono))),
                ctx);

        var respuesta = new SalidaInvitacion();
        salida.invitacionId().ifPresent(respuesta::setInvitacionId);
        respuesta.setMensaje(salida.mensaje());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // ------------------------------------- lo que este servicio le contesta a otros --

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<AliasResuelto> resolverAlias(String alias) {
        Traza.marcarCasoDeUso("CU-12", alias);
        return respuestas.resolverAlias(alias, sesion.actual());
    }

    @Override
    @Permiso("BILLETERA_VER")
    public ResponseEntity<ActividadEnGrupos> consultarActividad(UUID usuarioId) {
        Traza.marcarCasoDeUso("CU-16", usuarioId.toString());
        return respuestas.consultarActividad(usuarioId, sesion.actual());
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<PaqueteDelSorteo> consultarPaqueteDelSorteo(UUID sorteoId) {
        Traza.marcarCasoDeUso("CU-61", sorteoId.toString());
        return respuestas.consultarPaqueteDelSorteo(sorteoId, sesion.actual());
    }
}
