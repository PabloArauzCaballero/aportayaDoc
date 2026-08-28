package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.dominio.puertos.HechosDeOtrosServicios;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * De {@code {tipo, valor}} a una cuenta de billetera.
 *
 * <p>El contrato de CU-12 recibe el destino como lo escribe una persona —un alias o un
 * grupo— y el caso de uso trabaja con la cuenta. Traducir uno en otro es un paso
 * aparte **y anterior**: el alias se resuelve preguntandole a {@code grupos}, que es
 * una llamada de red, y una llamada de red no entra en la transaccion que mueve la
 * plata (invariante 6).
 *
 * <p>Por eso esto no vive dentro de CU-12: la transaccion del dinero empieza cuando el
 * destino ya es un identificador.
 */
@Service
public class ResolverDestino {

    private final CuentasDelDestino cuentas;
    private final HechosDeOtrosServicios afuera;

    public ResolverDestino(CuentasDelDestino cuentas, HechosDeOtrosServicios afuera) {
        this.cuentas = cuentas;
        this.afuera = afuera;
    }

    /**
     * La cuenta a la que apunta el destino, o vacio si no apunta a ninguna.
     *
     * @param tipo {@code ALIAS} o {@code GRUPO}, tal como lo declara el contrato.
     */
    public Optional<UUID> cuenta(String tipo, String valor, ContextoSesion ctx) {
        if ("GRUPO".equals(tipo)) {
            return identificador(valor).flatMap(grupoId -> cuentas.deGrupo(grupoId, ctx));
        }
        return afuera.usuarioDelAlias(valor).flatMap(usuario -> cuentas.deUsuario(usuario, ctx));
    }

    private static Optional<UUID> identificador(String valor) {
        try {
            return Optional.of(UUID.fromString(valor));
        } catch (IllegalArgumentException noEsUnId) {
            return Optional.empty();
        }
    }
}
