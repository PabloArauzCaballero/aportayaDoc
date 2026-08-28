package bo.aportaya.notificaciones.aplicacion;

import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.notificaciones.dominio.EleccionDeProveedor;
import bo.aportaya.notificaciones.dominio.EsperaDeReintento;
import bo.aportaya.notificaciones.infraestructura.EnvioRepositorio;
import bo.aportaya.notificaciones.infraestructura.EnvioRepositorio.EnCola;
import bo.aportaya.notificaciones.infraestructura.ProveedorRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-83 · Enrutar el envio por proveedor de mensajeria.
 *
 * <p>Toma de la cola con {@code FOR UPDATE SKIP LOCKED}: cada replica se lleva lo que
 * nadie tomo, y **el mismo mensaje no sale dos veces** aunque corran diez procesos.
 *
 * <p>El fallo de un proveedor **no descarta el mensaje**: reprograma con retroceso
 * exponencial y prueba el siguiente. Solo cuando se agotan todos va a cola muerta, que
 * es un lugar donde alguien mira, no un agujero.
 */
@Service
public class CU83DespacharLote {

    private final Datos datos;
    private final EnvioRepositorio envios;
    private final ProveedorRepositorio proveedores;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int umbralDeSalud;
    private final java.time.Duration techoDeEspera;
    private final AdaptadorMensajeria adaptador;

    public CU83DespacharLote(
            Datos datos,
            EnvioRepositorio envios,
            ProveedorRepositorio proveedores,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.proveedores.umbral-de-salud}") int umbralDeSalud,
            @Value("${aportaya.envios.techo-de-espera}") java.time.Duration techoDeEspera,
            AdaptadorMensajeria adaptador) {
        this.datos = datos;
        this.envios = envios;
        this.proveedores = proveedores;
        this.outbox = outbox;
        this.reloj = reloj;
        this.umbralDeSalud = umbralDeSalud;
        this.techoDeEspera = techoDeEspera;
        this.adaptador = adaptador;
    }

    /**
     * El puerto hacia afuera.
     *
     * <p>Una interfaz y no una clase concreta porque el default del proyecto es el
     * **simulador**: nada sale de verdad hasta que alguien conecte un adaptador real,
     * y eso es una decision de despliegue, no de este codigo.
     */
    public interface AdaptadorMensajeria {
        Resultado enviar(String proveedorCodigo, Canal canal, String destinatario, UUID envioId);

        record Resultado(boolean aceptado, String idMensajeProveedor, String codigoError) {}
    }

    @Transactional
    public SalidaLote ejecutar(EntradaLote entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            List<EnCola> lote = envios.tomar(dsl, entrada.particion(), ahora, entrada.cuantos());
            List<Detalle> detalles = new ArrayList<>();
            var candidatos = proveedores.activos(dsl);

            for (EnCola item : lote) {
                // Quien ya fallo para este envio no se vuelve a intentar en la misma
                // ronda: seria gastar el reintento en el proveedor que acaba de caerse.
                List<String> intentados = new ArrayList<>();
                Optional<EleccionDeProveedor.Candidato> elegido =
                        EleccionDeProveedor.elegir(candidatos, item.canal(), intentados, umbralDeSalud);

                if (elegido.isEmpty()) {
                    // AP-CU83-01: ningun proveedor sirve para ese canal. No se
                    // reintenta a ciegas; se saca de la cola y queda registrado.
                    envios.aColaMuerta(dsl, item.colaId(), item.envioId(), "SIN_PROVEEDOR_PARA_CANAL", "{}", ahora);
                    detalles.add(new Detalle(item.envioId(), null, "COLA_MUERTA", item.intentos()));
                    continue;
                }

                var resultado =
                        adaptador.enviar(elegido.get().codigo(), item.canal(), item.destinatario(), item.envioId());
                proveedores.registrarResultado(dsl, elegido.get().codigo(), resultado.aceptado());

                if (resultado.aceptado()) {
                    envios.marcarEnviado(dsl, item.envioId(), resultado.idMensajeProveedor(), ahora);
                    envios.sacarDeLaCola(dsl, item.colaId());
                    detalles.add(new Detalle(
                            item.envioId(), elegido.get().codigo(), "ENVIADO", (short) (item.intentos() + 1)));
                    outbox.emitir(
                            dsl,
                            new EventoDominio(
                                    "notificaciones.notificacion_enviada",
                                    "envio_notificacion",
                                    item.envioId(),
                                    Map.of(
                                            "proveedor",
                                            elegido.get().codigo(),
                                            "canal",
                                            item.canal().name()),
                                    UUID.fromString(ctx.traza().id())));
                    continue;
                }

                int siguienteIntento = item.intentos() + 1;
                if (siguienteIntento >= item.maxIntentos()) {
                    // AP-CU83-05. Cola muerta, con aviso: un mensaje que nadie va a
                    // mirar es lo mismo que un mensaje perdido.
                    envios.aColaMuerta(dsl, item.colaId(), item.envioId(), "AGOTADOS_LOS_REINTENTOS", "{}", ahora);
                    detalles.add(new Detalle(
                            item.envioId(), elegido.get().codigo(), "COLA_MUERTA", (short) siguienteIntento));
                    outbox.emitir(
                            dsl,
                            new EventoDominio(
                                    "notificaciones.notificacion_fallida",
                                    "envio_notificacion",
                                    item.envioId(),
                                    Map.of("motivo", "AGOTADOS_LOS_REINTENTOS"),
                                    UUID.fromString(ctx.traza().id())));
                } else {
                    OffsetDateTime proximo = ahora.plus(
                            EsperaDeReintento.para(item.envioId().toString(), siguienteIntento, techoDeEspera));
                    envios.reprogramar(dsl, item.colaId(), item.envioId(), proximo, resultado.codigoError());
                    detalles.add(
                            new Detalle(item.envioId(), elegido.get().codigo(), "EN_COLA", (short) siguienteIntento));
                }
            }

            return new SalidaLote(lote.size(), detalles);
        });
    }

    public record EntradaLote(String particion, int cuantos) {}

    public record SalidaLote(int tomados, List<Detalle> detalle) {}

    public record Detalle(UUID envioId, String proveedorCodigo, String estado, short intentos) {}
}
