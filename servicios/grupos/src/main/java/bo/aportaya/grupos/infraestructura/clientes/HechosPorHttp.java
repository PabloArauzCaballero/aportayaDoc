package bo.aportaya.grupos.infraestructura.clientes;

import bo.aportaya.grupos.dominio.puertos.HechosDeOtrosServicios;
import bo.aportaya.plataforma.web.clientes.ClienteDeServicio;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Le pregunta a los otros seis servicios, por sus contratos.
 *
 * <p>Cada respuesta que no llega se traduce a **la que no deja pasar**: el organizador
 * no esta habilitado, no hay tarifario, la licencia no cubre, el participante no esta
 * al dia, hay restriccion vigente, el contacto esta suprimido. Es denegar por omision
 * (invariante 9), y es deliberado: seguir adelante porque el servicio que valida estaba
 * caido es exactamente el hueco por donde entra lo que las reglas existen para frenar.
 *
 * <p>La unica excepcion es la reputacion: ahi «no se sabe» se responde como tal
 * —{@code tieneHistorial=false}— y decide quien pregunta. Tratar la falta de historial
 * como puntaje cero le cerraria la puerta a todo el que empieza, que es lo contrario de
 * lo que la regla busca.
 */
@Component
public class HechosPorHttp implements HechosDeOtrosServicios {

    private final ClienteDeServicio organizadores;
    private final ClienteDeServicio tarifas;
    private final ClienteDeServicio cumplimiento;
    private final ClienteDeServicio aportes;
    private final ClienteDeServicio transparencia;
    private final ClienteDeServicio garantia;
    private final ClienteDeServicio notificaciones;
    private final RestClient identidad;

    public HechosPorHttp(
            RestClient.Builder constructor,
            @Value("${aportaya.servicios.organizador}") String urlOrganizador,
            @Value("${aportaya.servicios.tarifas}") String urlTarifas,
            @Value("${aportaya.servicios.cumplimiento}") String urlCumplimiento,
            @Value("${aportaya.servicios.aportes}") String urlAportes,
            @Value("${aportaya.servicios.transparencia}") String urlTransparencia,
            @Value("${aportaya.servicios.garantia}") String urlGarantia,
            @Value("${aportaya.servicios.notificaciones}") String urlNotificaciones,
            @Value("${aportaya.servicios.identidad}") String urlIdentidad) {
        this.organizadores = new ClienteDeServicio(constructor, urlOrganizador, "organizador");
        this.tarifas = new ClienteDeServicio(constructor, urlTarifas, "tarifas");
        this.cumplimiento = new ClienteDeServicio(constructor, urlCumplimiento, "cumplimiento");
        this.aportes = new ClienteDeServicio(constructor, urlAportes, "aportes");
        this.transparencia = new ClienteDeServicio(constructor, urlTransparencia, "transparencia");
        this.garantia = new ClienteDeServicio(constructor, urlGarantia, "garantia");
        this.notificaciones = new ClienteDeServicio(constructor, urlNotificaciones, "notificaciones");
        this.identidad = constructor.baseUrl(urlIdentidad).build();
    }

    @Override
    public boolean organizadorHabilitado(UUID organizadorId) {
        return organizadores
                .consultar("/organizadores/" + organizadorId + "/habilitacion", Habilitacion.class)
                .map(Habilitacion::habilitado)
                .orElse(false);
    }

    @Override
    public Optional<UUID> tarifarioVigente(String codigo) {
        return tarifas.consultar("/tarifas/vigentes/" + codigo, Tarifario.class)
                .filter(Tarifario::vigente)
                .map(Tarifario::tarifarioId);
    }

    @Override
    public boolean licenciaHabilita(String servicio) {
        return cumplimiento
                .consultar("/licencia/alcance?servicio=" + servicio, Alcance.class)
                .map(Alcance::habilitado)
                .orElse(false);
    }

    @Override
    public EstadoDePagos estadoDePagos(UUID participanteId) {
        return aportes.consultar("/aportes/participantes/" + participanteId + "/estado", Estado.class)
                .map(e -> new EstadoDePagos(
                        e.alDia(),
                        new BigDecimal(e.totalAportado()),
                        new BigDecimal(e.deudaVigente()),
                        new BigDecimal(e.porAportar()),
                        e.obligacionesAbiertas(),
                        e.moneda()))
                // Sin respuesta no se afirma que esta al dia: es lo unico que no se
                // puede suponer sin arriesgar el turno de otro.
                .orElse(new EstadoDePagos(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, "BOB"));
    }

    @Override
    public int morososDelGrupo(UUID grupoId) {
        return aportes.consultar("/aportes/grupos/" + grupoId + "/morosos", Morosos.class)
                .map(Morosos::morosos)
                .orElse(Integer.MAX_VALUE);
    }

    @Override
    public Reputacion reputacion(UUID usuarioId) {
        return transparencia
                .consultar("/reputacion/" + usuarioId + "/puntaje", Puntaje.class)
                .map(p -> new Reputacion(p.tieneHistorial(), new BigDecimal(p.puntaje())))
                .orElse(new Reputacion(false, BigDecimal.ZERO));
    }

    @Override
    public Restriccion restriccion(UUID usuarioId) {
        return garantia.consultar("/cobranza/restricciones/vigentes/" + usuarioId, Restringido.class)
                .map(r -> new Restriccion(r.vigente(), new BigDecimal(r.montoQueLaLevanta())))
                // Sin respuesta se asume restringido: dejar entrar a alguien porque el
                // servicio que lo verifica no contesto es el error que no se paga solo.
                .orElse(new Restriccion(true, BigDecimal.ZERO));
    }

    @Override
    public boolean contactoSuprimido(String identificador, String categoria) {
        return notificaciones
                .consultar(
                        "/notificaciones/supresion?identificador=" + identificador + "&categoria=" + categoria,
                        Suprimido.class)
                .map(Suprimido::suprimido)
                .orElse(true);
    }

    @Override
    public UUID tokenDeInvitacion(String canal, String destinoEnmascarado) {
        var emitido = identidad
                .post()
                .uri("/usuarios/tokens/invitacion")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .headers(ClienteDeServicio::propagarElToken)
                .body(Map.of("canal", canal, "destinoEnmascarado", destinoEnmascarado))
                .retrieve()
                .body(Token.class);
        if (emitido == null) {
            // Sin token no hay enlace, y sin enlace no hay invitacion que enviar.
            throw new IllegalStateException("identidad no emitio el token de invitacion");
        }
        return emitido.tokenId();
    }

    @Override
    public Optional<UUID> usuarioDelTelefono(String telefonoE164) {
        try {
            var encontrado = identidad
                    .get()
                    .uri("/usuarios/por-telefono?telefono={t}", telefonoE164)
                    .headers(ClienteDeServicio::propagarElToken)
                    .retrieve()
                    .body(Encontrado.class);
            return encontrado != null && encontrado.existe()
                    ? Optional.ofNullable(encontrado.usuarioId())
                    : Optional.empty();
        } catch (RuntimeException noSePudo) {
            // Sin respuesta se asume que no tiene cuenta: invitar de mas es ruido;
            // no invitar por una consulta caida es perder a alguien que si podia entrar.
            return Optional.empty();
        }
    }

    private record Encontrado(boolean existe, UUID usuarioId) {}

    private record Habilitacion(boolean habilitado) {}

    private record Tarifario(boolean vigente, UUID tarifarioId) {}

    private record Alcance(boolean habilitado) {}

    private record Estado(
            boolean alDia,
            String totalAportado,
            String deudaVigente,
            String porAportar,
            int obligacionesAbiertas,
            String moneda) {}

    private record Morosos(int morosos) {}

    private record Puntaje(boolean tieneHistorial, String puntaje) {}

    private record Restringido(boolean vigente, String montoQueLaLevanta) {}

    private record Suprimido(boolean suprimido) {}

    private record Token(UUID tokenId) {}
}
