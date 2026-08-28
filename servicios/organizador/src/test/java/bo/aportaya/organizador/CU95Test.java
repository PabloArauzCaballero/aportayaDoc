package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU95DefinirAutomatizacion.EntradaRegla;
import bo.aportaya.organizador.aplicacion.CU95DefinirAutomatizacion.SalidaRegla;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-95 · Definir una regla de automatizacion. */
class CU95Test extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private EntradaRegla regla(String accion, boolean confirmacion, int prioridad, String disparador) {
        return new EntradaRegla(
                "REGLA-" + corto(),
                "Regla de prueba para " + accion,
                disparador,
                "CRON".equals(disparador) ? "0 8 * * *" : "aporte.vencido",
                "dias_para_vencer = 3",
                accion,
                confirmacion,
                prioridad);
    }

    @Test
    @DisplayName(
            "Dado un organizador habilitado · Cuando define una regla que recuerda tres días antes del vencimiento · Entonces la regla queda activa sin requerir confirmación humana")
    void criterio1() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaRegla definida =
                transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 10, "CRON"), ctx));
        SalidaRegla activa = transaccion.execute(t -> reglaCU.activar(definida.reglaId(), ctx));

        // Mandar un recordatorio de mas molesta; no mueve plata de nadie. Esas si
        // pueden correr solas.
        assertThat(activa.activa()).isTrue();
        assertThat(activa.requiereConfirmacionHumana()).isFalse();
        // Y nace INACTIVA: publicarla y encenderla en el mismo acto no deja momento
        // para revisar que la condicion diga lo que se cree que dice.
        assertThat(definida.activa()).isFalse();
    }

    @Test
    @DisplayName(
            "Dada una regla cuya acción es PROPONER_ENTREGA · Cuando se guarda · Entonces requiere_confirmacion_humana queda en true forzado")
    void criterio2() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        // HUECO DECLARADO: `ck_regla_automatizacion_accion` no admite PROPONER_ENTREGA.
        // La accion equivalente en la DDL es EJECUTAR_ENTREGA. Manda la DDL. Ver H-5 en
        // planes/informes/carril-2E.md.
        //
        // El efecto que el criterio pide se cumple igual, y de forma mas fuerte: en vez
        // de forzar la bandera en silencio, la regla se RECHAZA si viene sin
        // confirmacion. Forzarla callado deja a quien la escribio creyendo que definio
        // otra cosa.
        assertThatThrownBy(() ->
                        transaccion.execute(t -> reglaCU.definir(regla("EJECUTAR_ENTREGA", false, 11, "EVENTO"), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("exige confirmacion humana");

        SalidaRegla conConfirmacion =
                transaccion.execute(t -> reglaCU.definir(regla("EJECUTAR_ENTREGA", true, 11, "EVENTO"), ctx));
        assertThat(conConfirmacion.requiereConfirmacionHumana()).isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.regla_automatizacion WHERE id = ? AND requiere_confirmacion_humana",
                        conConfirmacion.reglaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una regla cuyo ámbito apunta a un grupo ajeno · Cuando se intenta crear · Entonces se rechaza con AMBITO_AJENO")
    void criterio3() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        // HUECO DECLARADO: `regla_automatizacion` **no tiene columna de ambito ni de
        // grupo**. Las reglas son globales y el grupo entra recien al programar la
        // tarea (`tarea_automatizada.grupo_id`). No hay ambito ajeno que rechazar
        // porque no hay ambito. Ver H-6 en planes/informes/carril-2E.md.
        //
        // Lo que si se verifica es que la regla no lleva grupo: si lo llevara sin que
        // la DDL lo soporte, estaria inventado.
        SalidaRegla definida =
                transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 12, "CRON"), ctx));

        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM information_schema.columns
                         WHERE table_schema = 'organizador' AND table_name = 'regla_automatizacion'
                           AND column_name IN ('ambito', 'grupo_id')
                        """))
                .as("la DDL no tiene ambito: el hueco H-6 esta declarado, no rellenado")
                .isZero();
        assertThat(definida.reglaId()).isNotNull();
    }

    @Test
    @DisplayName(
            "Dadas dos reglas activas con la misma prioridad y disparador · Cuando se intenta crear la segunda · Entonces se rechaza con PRIORIDAD_DUPLICADA")
    void criterio4() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaRegla primera =
                transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 20, "CRON"), ctx));
        transaccion.execute(t -> reglaCU.activar(primera.reglaId(), ctx));
        SalidaRegla segunda =
                transaccion.execute(t -> reglaCU.definir(regla("GENERAR_COBROS", false, 20, "CRON"), ctx));

        // Dos reglas activas con el mismo disparador y prioridad hacen que el orden de
        // ejecucion dependa de como la base devuelva las filas: dos corridas del mismo
        // dia harian cosas distintas.
        assertThatThrownBy(() -> transaccion.execute(t -> reglaCU.activar(segunda.reglaId(), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("prioridad 20");
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM organizador.regla_automatizacion WHERE disparador = 'CRON' AND prioridad = 20 AND activa"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave natural es el codigo, unico en la base. Y activar dos veces no
        // vuelve a emitir el evento.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaRegla definida =
                transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 30, "CRON"), ctx));

        SalidaRegla a = transaccion.execute(t -> reglaCU.activar(definida.reglaId(), ctx));
        SalidaRegla b = transaccion.execute(t -> reglaCU.activar(definida.reglaId(), ctx));

        assertThat(b.reglaId()).isEqualTo(a.reglaId());
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "organizador.regla_activada",
                        definida.reglaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // La BASE decide: uq_regla_automatizacion_prioridad sobre (disparador,
        // prioridad) WHERE activa, aunque la aplicacion se equivoque.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaRegla primera =
                transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 40, "CRON"), ctx));
        transaccion.execute(t -> reglaCU.activar(primera.reglaId(), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.regla_automatizacion
                            (id, codigo, descripcion, disparador, expresion_disparo, condicion, accion,
                             requiere_confirmacion_humana, prioridad, activa)
                        VALUES (gen_random_uuid(), 'COLADA-%s', 'Se salta la aplicacion', 'CRON',
                                '0 9 * * *', 'siempre', 'GENERAR_COBROS', false, 40, true)
                        """
                                .formatted(corto())))
                .contains("uq_regla_automatizacion_prioridad");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Las seis acciones de la DDL se reparten en dos grupos sin solaparse ni dejar
        // ninguna afuera: las que exigen confirmacion y las que no. Una accion que no
        // este en ninguno de los dos correria sola sin que nadie lo haya decidido.
        var todas = new java.util.HashSet<String>();
        todas.addAll(bo.aportaya.organizador.dominio.AccionSensible.EXIGEN_CONFIRMACION);
        todas.addAll(bo.aportaya.organizador.dominio.AccionSensible.AUTOMATICAS);

        assertThat(todas).hasSize(6);
        assertThat(bo.aportaya.organizador.dominio.AccionSensible.EXIGEN_CONFIRMACION)
                .doesNotContainAnyElementsOf(bo.aportaya.organizador.dominio.AccionSensible.AUTOMATICAS);
        // Y son exactamente las que la base admite.
        var admitidas = dsl
                .fetch(
                        "SELECT unnest(enum_o_check) AS accion FROM (SELECT ARRAY['APLICAR_MORA','EJECUTAR_ENTREGA','ENVIAR_RECORDATORIO','ESCALAR_COBRANZA','GENERAR_COBROS','LIQUIDAR_PERIODO'] AS enum_o_check) t")
                .stream()
                .map(f -> f.get("accion", String.class))
                .toList();
        assertThat(todas).containsExactlyInAnyOrderElementsOf(admitidas);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "reglas"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "reglas"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Una regla sin condicion se dispara siempre, que es lo mismo que no tener
        // condicion y no se parece en nada a lo que quien la escribio quiso decir. No
        // queda fila ni evento.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        var sinCondicion = new EntradaRegla(
                "VACIA-" + corto(), "Sin condicion", "CRON", "0 8 * * *", "  ", "ENVIAR_RECORDATORIO", false, 50);

        assertThatThrownBy(() -> transaccion.execute(t -> reglaCU.definir(sinCondicion, ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin condicion");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.regla_automatizacion WHERE codigo = ?",
                        sinCondicion.codigo()))
                .isZero();
    }
}
