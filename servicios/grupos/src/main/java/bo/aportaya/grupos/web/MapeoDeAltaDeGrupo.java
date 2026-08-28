package bo.aportaya.grupos.web;

import bo.aportaya.grupos.aplicacion.CU20CrearGrupo;
import bo.aportaya.grupos.dominio.GrupoNuevo;
import bo.aportaya.grupos.dominio.puertos.HechosDeOtrosServicios;
import bo.aportaya.grupos.web.generado.modelo.EntradaGrupo;
import java.util.Optional;
import java.util.UUID;

/**
 * Como se arma la entrada de CU-20 con los tres hechos que no son de este servicio.
 *
 * <p>Un grupo no se abre si el organizador no esta habilitado, si no hay tarifario
 * vigente o si la licencia no cubre el servicio. Los tres los contestan otros
 * —{@code organizador}, {@code tarifas} y {@code cumplimiento}— y los tres se preguntan
 * **antes** de abrir la transaccion (invariante 6).
 *
 * <p>Cuando alguno no responde, la respuesta es la que no deja pasar. Abrir un pasanaku
 * porque el servicio que valida estaba caido es abrirlo sin precio congelado o sin
 * licencia, y eso lo paga la gente que entra.
 */
final class MapeoDeAltaDeGrupo {

    private MapeoDeAltaDeGrupo() {}

    static CU20CrearGrupo.EntradaCreacion entrada(
            EntradaGrupo cuerpo, HechosDeOtrosServicios afuera, String codigoTarifario, String servicioDeLicencia) {

        Optional<UUID> organizador = Optional.ofNullable(cuerpo.getOrganizadorId());

        return new CU20CrearGrupo.EntradaCreacion(
                new GrupoNuevo(
                        cuerpo.getNombre(),
                        MapeoDeGrupos.dinero(cuerpo.getMontoAporte()),
                        cuerpo.getPeriodicidad().getValue(),
                        cuerpo.getDiaCobro(),
                        cuerpo.getCupos(),
                        cuerpo.getFechaDeInicio()),
                organizador,
                // Un grupo sin organizador lo lleva la plataforma: no hay a quien
                // pedirle habilitacion, y nadie cobra por administrarlo (RN-18).
                organizador.map(afuera::organizadorHabilitado).orElse(true),
                afuera.tarifarioVigente(codigoTarifario),
                afuera.licenciaHabilita(servicioDeLicencia),
                Boolean.TRUE.equals(cuerpo.getPermitePermutaDeTurnos()));
    }
}
