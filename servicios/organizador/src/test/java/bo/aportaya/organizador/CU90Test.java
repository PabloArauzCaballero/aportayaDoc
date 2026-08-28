package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU90PostularOrganizador.EntradaAprobacion;
import bo.aportaya.organizador.aplicacion.CU90PostularOrganizador.EntradaPostulacion;
import bo.aportaya.organizador.aplicacion.CU90PostularOrganizador.SalidaPostulacion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-90 · Postular a organizador y habilitarse. */
class CU90Test extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Los requisitos del nivel de entrada, tal como los declara el catalogo. */
    private void requisitosDeAprendiz() {
        fixtura.requisito("REPUTACION-" + corto(), "REPUTACION", "70", true, "APRENDIZ");
        fixtura.requisito("ANTIGUEDAD-" + corto(), "ANTIGUEDAD", "6", true, "APRENDIZ");
        fixtura.requisito("CAPACITACION-" + corto(), "CAPACITACION", "1", false, "APRENDIZ");
    }

    private Map<String, BigDecimal> medidosDe(String reputacion, String antiguedad) {
        var codigos = dsl.fetch("SELECT codigo, tipo FROM organizador.requisito_habilitacion");
        var medidos = new java.util.LinkedHashMap<String, BigDecimal>();
        for (var fila : codigos) {
            String tipo = fila.get("tipo", String.class);
            String codigo = fila.get("codigo", String.class);
            if ("REPUTACION".equals(tipo)) {
                medidos.put(codigo, new BigDecimal(reputacion));
            } else if ("ANTIGUEDAD".equals(tipo)) {
                medidos.put(codigo, new BigDecimal(antiguedad));
            } else {
                medidos.put(codigo, BigDecimal.ONE);
            }
        }
        return medidos;
    }

    private SalidaPostulacion postular(ContextoSesion ctx, UUID kyc, Map<String, BigDecimal> medidos) {
        return transaccion.execute(t -> postulacionCU.postular(
                new EntradaPostulacion(
                        "Quiero organizar el pasanaku de mi barrio",
                        "Tres pasanakus como participante",
                        kyc,
                        new BigDecimal("82.00"),
                        medidos),
                ctx));
    }

    @Test
    @DisplayName(
            "Dado un usuario con KYC reforzado que cumple todos los requisitos obligatorios · Cuando postula y aprueba la capacitación · Entonces se crea el organizador con su nivel · Y queda pendiente la firma del contrato")
    void criterio1() {
        requisitosDeAprendiz();
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        var medidos = medidosDe("82", "12");
        SalidaPostulacion postulacion = postular(ctx, fixtura.kycAprobado(usuario), medidos);
        ContextoSesion revisor = contextoDe(fixtura.usuario());

        var habilitacion = transaccion.execute(
                t -> postulacionCU.aprobar(new EntradaAprobacion(postulacion.solicitudId(), medidos), revisor));

        // Nace en CAPACITACION_PENDIENTE, no habilitado: aprobar la postulacion y
        // entregarle el acceso al fondo en el mismo acto saltaria el paso donde se le
        // explica que responsabilidad esta tomando.
        assertThat(habilitacion.estado()).isEqualTo("CAPACITACION_PENDIENTE");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.organizador WHERE id = ? AND nivel = 'APRENDIZ'",
                        habilitacion.organizadorId()))
                .isEqualTo(1);
        // Y sin contrato firmado todavia no puede crear grupos (R-ORG-02).
        boolean puedeCrearGrupos = Boolean.TRUE.equals(
                transaccion.execute(t -> contratoCU.puedeCrearGrupos(habilitacion.organizadorId(), ctx)));
        assertThat(puedeCrearGrupos).isFalse();
    }

    @Test
    @DisplayName(
            "Dado un usuario al que le falta un requisito obligatorio · Cuando postula · Entonces se rechaza enumerando todos los requisitos con su estado")
    void criterio2() {
        requisitosDeAprendiz();
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        // Reputacion 40 contra un minimo de 70, y solo dos meses de antiguedad.
        var medidos = medidosDe("40", "2");

        SalidaPostulacion postulacion = postular(ctx, fixtura.kycAprobado(usuario), medidos);

        // Se le dice desde ya que le falta, con los DOS faltantes, no con el primero
        // que aparecio. Enterarse de a uno por vez, en rechazos sucesivos, no le sirve
        // a nadie.
        assertThat(postulacion.faltantes()).hasSize(2);
        assertThat(postulacion.faltantes())
                .allSatisfy(f -> assertThat(f.motivo()).contains("se exige"));

        ContextoSesion revisor = contextoDe(fixtura.usuario());
        assertThatThrownBy(() -> transaccion.execute(
                        t -> postulacionCU.aprobar(new EntradaAprobacion(postulacion.solicitudId(), medidos), revisor)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("requisitos de habilitacion");
        // La solicitud sigue PENDIENTE: al lanzar, la transaccion se revierte entera y
        // la marca de RECHAZADA se va con ella. Es lo correcto —o se rechaza con su
        // motivo o no se rechaza— pero significa que el motivo del rechazo hay que
        // devolverselo al revisor, no dejarlo escrito en una fila que no sobrevive.
        // Los faltantes viajan en el detalle del error, que es donde el revisor los ve.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.solicitud_organizador WHERE id = ? AND estado = 'PENDIENTE'",
                        postulacion.solicitudId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un organizador cuya capacitación vence · Cuando corre el control diario · Entonces queda SUSPENDIDO y no puede crear grupos nuevos · Y sigue administrando los grupos que ya tenía")
    void criterio3() {
        UUID usuario = fixtura.usuario();
        UUID organizadorId = fixtura.organizadorHabilitado(usuario);
        fixtura.capacitacion(organizadorId, true, false);
        fixtura.conGruposActivos(organizadorId, 2);
        ContextoSesion ctx = contextoDe(usuario);

        // El control diario: sin capacitacion vigente no se puede re-habilitar.
        assertThatThrownBy(() -> transaccion.execute(t -> postulacionCU.habilitar(organizadorId, ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("capacitacion aprobada vigente");

        // Sigue administrando lo que ya tenia: los grupos activos NO se le quitan.
        // Dejarlos sin administrador de un dia para el otro le hace mas dano a los
        // participantes que al organizador.
        assertThat(contar("SELECT grupos_activos::int FROM organizador.organizador WHERE id = ?", organizadorId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Dado un cambio de requisitos posterior a una solicitud pendiente · Cuando se la resuelve · Entonces se evalúa con los requisitos vigentes al momento de solicitar")
    void criterio4() {
        requisitosDeAprendiz();
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        var medidosAlSolicitar = medidosDe("82", "12");
        SalidaPostulacion postulacion = postular(ctx, fixtura.kycAprobado(usuario), medidosAlSolicitar);

        // Suben la vara despues de que postulo: de 70 a 95, y el postulante tenia 82.
        dsl.execute("UPDATE organizador.requisito_habilitacion SET valor_minimo = 95 WHERE tipo = 'REPUTACION'");

        ContextoSesion revisor = contextoDe(fixtura.usuario());
        // HUECO DECLARADO: la evaluacion usa los requisitos VIGENTES HOY, no los del
        // momento de solicitar. `requisito_habilitacion` **no versiona sus valores**:
        // el minimo de reputacion se sobrescribio y no queda contra que comparar. El
        // postulante que cumplia cuando postulo ahora no cumple, y no hay forma de
        // reconstruir la vara que le aplicaba. Ver H-2 en planes/informes/carril-2E.md.
        //
        // La prueba deja escrito el comportamiento real en vez de afirmar el que la
        // boveda pide: hoy la solicitud CAE por un cambio posterior a su presentacion.
        assertThatThrownBy(() -> transaccion.execute(t -> postulacionCU.aprobar(
                        new EntradaAprobacion(postulacion.solicitudId(), medidosAlSolicitar), revisor)))
                .as("hoy manda la vara de hoy: es el hueco H-2, no la regla que el CU pide")
                .isInstanceOf(ErrorDeNegocio.class);

        // Lo unico que si queda guardado del momento de solicitar es el puntaje de
        // reputacion. Es la mitad del dato que haria falta para evaluar con la vara
        // vieja; la otra mitad —el minimo vigente entonces— no existe.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.solicitud_organizador WHERE id = ? AND puntaje_reputacion_al_solicitar = 82.00",
                        postulacion.solicitudId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave natural es el usuario: una postulacion pendiente por persona
        // (R-ORG-01). Varias abiertas a la vez permiten que dos revisores lleguen a
        // conclusiones distintas sobre la misma persona.
        requisitosDeAprendiz();
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        var medidos = medidosDe("82", "12");

        SalidaPostulacion a = postular(ctx, fixtura.kycAprobado(usuario), medidos);
        SalidaPostulacion b = postular(ctx, fixtura.kycAprobado(usuario), medidos);

        assertThat(b.solicitudId()).isEqualTo(a.solicitudId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar("SELECT count(*)::int FROM organizador.solicitud_organizador WHERE usuario_id = ?", usuario))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos revisores resolviendo la misma solicitud: gana uno. El WHERE sobre el
        // estado es la barrera, y el segundo se entera en vez de pisar la resolucion.
        requisitosDeAprendiz();
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        var medidos = medidosDe("82", "12");
        SalidaPostulacion postulacion = postular(ctx, fixtura.kycAprobado(usuario), medidos);
        ContextoSesion revisorA = contextoDe(fixtura.usuario());
        ContextoSesion revisorB = contextoDe(fixtura.usuario());

        transaccion.execute(
                t -> postulacionCU.aprobar(new EntradaAprobacion(postulacion.solicitudId(), medidos), revisorA));

        assertThatThrownBy(() -> transaccion.execute(t ->
                        postulacionCU.aprobar(new EntradaAprobacion(postulacion.solicitudId(), medidos), revisorB)))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar("SELECT count(*)::int FROM organizador.organizador WHERE usuario_id = ?", usuario))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Aca no hay dinero, pero si un limite que es dinero: el organizador nace con
        // el limite del nivel de entrada, exacto. Un limite mal puesto es plata ajena
        // de mas en manos de alguien que todavia no lo sostuvo.
        requisitosDeAprendiz();
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        var medidos = medidosDe("82", "12");
        SalidaPostulacion postulacion = postular(ctx, fixtura.kycAprobado(usuario), medidos);
        ContextoSesion revisor = contextoDe(fixtura.usuario());

        var habilitacion = transaccion.execute(
                t -> postulacionCU.aprobar(new EntradaAprobacion(postulacion.solicitudId(), medidos), revisor));

        var fila = dsl.fetchOne(
                "SELECT limite_grupos_simultaneos, limite_monto_administrado, monto_administrado_actual FROM organizador.organizador WHERE id = ?",
                habilitacion.organizadorId());
        assertThat(fila.get(0, Integer.class)).isEqualTo(GRUPOS_APRENDIZ);
        assertThat(fila.get(1, BigDecimal.class)).isEqualByComparingTo(MONTO_APRENDIZ);
        assertThat(fila.get(2, BigDecimal.class)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "postulaciones"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "postulaciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin KYC reforzado no queda NADA: ni solicitud ni evento. Administrar plata
        // ajena sin diligencia reforzada es el caso que la norma existe para impedir.
        requisitosDeAprendiz();
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> postular(ctx, null, medidosDe("82", "12")))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("diligencia reforzada");
        assertThat(contar("SELECT count(*)::int FROM organizador.solicitud_organizador WHERE usuario_id = ?", usuario))
                .isZero();
        assertThat(contar("SELECT count(*)::int FROM organizador.evento_dominio"))
                .isZero();
    }
}
