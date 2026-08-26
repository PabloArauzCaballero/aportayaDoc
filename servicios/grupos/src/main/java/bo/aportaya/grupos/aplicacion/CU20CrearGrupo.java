package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.GrupoNuevo;
import bo.aportaya.grupos.infraestructura.CreacionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-20 · Crear el grupo — <b>la parte que es de {@code grupos}</b>.
 *
 * <p>El caso de uso pide que en la misma transaccion se congele el tarifario
 * ({@code tarifas.tarifa_congelada_grupo}), se abra la cuenta del grupo
 * ({@code nucleo_financiero.cuenta_billetera}) y su espejo contable. Los tres viven en
 * esquemas ajenos, y {@code svc_grupos} no escribe ahi.
 *
 * <p>Misma respuesta que en CU-01, por la misma razon: <b>coreografia</b>. El grupo
 * nace {@code BORRADOR} y emite {@code grupos.grupo_creado}; {@code tarifas} congela
 * el precio y {@code nucleo-financiero} abre la cuenta al consumirlo. El grupo pasa a
 * {@code ABIERTO_A_INSCRIPCION} cuando los dos respondieron.
 *
 * <p>Que nazca en borrador no es un rodeo: <b>un grupo sin precio congelado no puede
 * recibir a nadie</b>, porque el precio es justamente lo que cada participante acepta
 * al firmar el reglamento. Dejarlo abierto antes seria pedirle a alguien que se
 * comprometa a un costo que todavia no existe.
 */
@Service
public class CU20CrearGrupo {

    private final Datos datos;
    private final CreacionRepositorio creacion;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Ids ids;

    /**
     * El quorum por omision, como FRACCION —{@code quorum_decisiones} es
     * {@code numeric(4,3)}—. Viene de configuracion y no de una constante: cuanto
     * hace falta para que un grupo decida es una decision de producto, y la regla
     * `sin-umbral-literal` tiene razon en no dejarla escrita en el codigo.
     */
    private final BigDecimal quorumPorOmision;

    public CU20CrearGrupo(
            Datos datos,
            CreacionRepositorio creacion,
            Outbox outbox,
            Reloj reloj,
            Ids ids,
            @org.springframework.beans.factory.annotation.Value("${aportaya.grupos.quorum-por-omision}")
                    BigDecimal quorumPorOmision) {
        this.datos = datos;
        this.creacion = creacion;
        this.outbox = outbox;
        this.reloj = reloj;
        this.ids = ids;
        this.quorumPorOmision = quorumPorOmision;
    }

    @Transactional
    public SalidaCreacion ejecutar(EntradaCreacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        // El precio no puede quedar indefinido: sin tarifario vigente no hay grupo.
        if (entrada.tarifarioVigenteId().isEmpty()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(20, 1), "No hay un tarifario vigente, asi que todavia no se puede crear el grupo.");
        }
        if (entrada.organizador().isPresent() && !entrada.organizadorHabilitado()) {
            throw new ErrorDeNegocio(CodigoError.de(20, 2), "Ese organizador no esta habilitado.");
        }
        if (!entrada.licenciaHabilitaElServicio()) {
            throw new ErrorDeNegocio(CodigoError.de(20, 4), "El servicio no esta habilitado en este momento.");
        }

        return datos.conContexto(ctx, dsl -> {
            UUID grupo = creacion.crear(
                    dsl, entrada.datos(), codigoPublico(), entrada.organizador(), quorumPorOmision, ahora);
            creacion.configurar(dsl, grupo, entrada.permitePermutaDeTurnos());

            String reglamento = textoDelReglamento(entrada.datos());
            creacion.redactarReglamento(dsl, grupo, reglamento, hash(reglamento), ctx.usuarioId(), ahora);
            creacion.abrirCupos(dsl, grupo, entrada.datos().cupos(), ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.grupo_creado",
                            "grupo",
                            grupo,
                            Map.of(
                                    "grupoId", grupo.toString(),
                                    "tarifarioId",
                                            entrada.tarifarioVigenteId()
                                                    .orElseThrow()
                                                    .toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCreacion(grupo, entrada.datos().fondoPorPeriodo().toString());
        });
    }

    /**
     * El texto que cada participante firma. Se guarda con su hash porque lo que se
     * acepta es un texto EXACTO: sin el hash, «acepte el reglamento» no dice cual.
     */
    private String textoDelReglamento(GrupoNuevo datos) {
        return """
               Reglamento de %s.
               Aporte: %s %s por periodo, %s, dia %d.
               Participantes: %d. Cada uno cobra una vez.
               Fondo de cada periodo: %s %s.
               """
                .formatted(
                        datos.nombre(),
                        datos.montoDelAporte().moneda(),
                        datos.montoDelAporte(),
                        datos.periodicidad().toLowerCase(java.util.Locale.ROOT),
                        datos.diaDeCobro(),
                        datos.cupos(),
                        datos.montoDelAporte().moneda(),
                        datos.fondoPorPeriodo());
    }

    private String codigoPublico() {
        return "GR-" + ids.nuevo().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private String hash(String texto) {
        try {
            byte[] digestion = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder(digestion.length * 2);
            for (byte b : digestion) {
                hexadecimal.append("%02x".formatted(b));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tiene que existir en cualquier JVM", e);
        }
    }

    public record EntradaCreacion(
            GrupoNuevo datos,
            Optional<UUID> organizador,
            boolean organizadorHabilitado,
            Optional<UUID> tarifarioVigenteId,
            boolean licenciaHabilitaElServicio,
            boolean permitePermutaDeTurnos) {}

    public record SalidaCreacion(UUID grupoId, String fondoPorPeriodo) {}
}
