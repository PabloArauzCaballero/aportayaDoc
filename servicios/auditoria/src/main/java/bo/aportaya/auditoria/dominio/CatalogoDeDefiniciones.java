package bo.aportaya.auditoria.dominio;

import java.util.Map;
import java.util.Optional;

/**
 * El catalogo de definiciones, en codigo.
 *
 * > [!warning] Hueco declarado
 * > `auditoria.indicador_kpi` guarda el numero pero **no la definicion**: no tiene
 * > columna de familia, de dueno, de sentido de la meta ni de version. Sin una tabla
 * > donde vivan, la definicion tiene que estar en algun lado, y el menos malo es
 * > aca: se versiona con el repositorio, se revisa en un PR y no se puede cambiar en
 * > caliente.
 * >
 * > **Lo correcto es una tabla `definicion_indicador`**, porque la skill
 * > `indicadores-tablero` pide que la definicion se versione y que el valor guarde
 * > con cual se calculo — y eso, en codigo, se pierde en cuanto alguien despliega.
 * > Es un cambio de modelo y por lo tanto troncal: queda anotado, no resuelto.
 */
public final class CatalogoDeDefiniciones {

    private static final Map<String, DefinicionDeIndicador> POR_CODIGO = Map.ofEntries(
            definicion(
                    "GRUPOS_ACTIVOS", FamiliaDeIndicador.NEGOCIO, "Gerencia comercial", SentidoDeMeta.MAYOR_ES_MEJOR),
            definicion(
                    "VOLUMEN_APORTADO", FamiliaDeIndicador.NEGOCIO, "Gerencia comercial", SentidoDeMeta.MAYOR_ES_MEJOR),
            definicion(
                    "PARTICIPANTES_ACTIVOS",
                    FamiliaDeIndicador.NEGOCIO,
                    "Gerencia comercial",
                    SentidoDeMeta.MAYOR_ES_MEJOR),
            definicion(
                    "TASA_DE_MOROSIDAD",
                    FamiliaDeIndicador.RIESGO,
                    "Gerencia de riesgos",
                    SentidoDeMeta.MENOR_ES_MEJOR),
            definicion(
                    "COBERTURAS_CONSUMIDAS",
                    FamiliaDeIndicador.RIESGO,
                    "Gerencia de riesgos",
                    SentidoDeMeta.MENOR_ES_MEJOR),
            definicion(
                    "ALERTAS_ABIERTAS", FamiliaDeIndicador.RIESGO, "Gerencia de riesgos", SentidoDeMeta.MENOR_ES_MEJOR),
            definicion(
                    "REPORTES_EN_PLAZO",
                    FamiliaDeIndicador.CUMPLIMIENTO,
                    "Oficial de cumplimiento",
                    SentidoDeMeta.MAYOR_ES_MEJOR),
            definicion(
                    "ALERTAS_SIN_CONCLUSION",
                    FamiliaDeIndicador.CUMPLIMIENTO,
                    "Oficial de cumplimiento",
                    SentidoDeMeta.MENOR_ES_MEJOR),
            definicion(
                    "CIERRES_CUADRADOS",
                    FamiliaDeIndicador.OPERACION,
                    "Gerencia de operaciones",
                    SentidoDeMeta.MAYOR_ES_MEJOR),
            definicion(
                    "INCIDENCIAS_SLA_VENCIDO",
                    FamiliaDeIndicador.OPERACION,
                    "Gerencia de operaciones",
                    SentidoDeMeta.MENOR_ES_MEJOR),
            definicion(
                    "INGRESOS_DEVENGADOS",
                    FamiliaDeIndicador.FINANZAS,
                    "Gerencia de finanzas",
                    SentidoDeMeta.MAYOR_ES_MEJOR),
            definicion(
                    "INGRESOS_COBRADOS",
                    FamiliaDeIndicador.FINANZAS,
                    "Gerencia de finanzas",
                    SentidoDeMeta.MAYOR_ES_MEJOR),
            definicion("ENCAJE", FamiliaDeIndicador.FINANZAS, "Gerencia de finanzas", SentidoDeMeta.MAYOR_ES_MEJOR));

    private CatalogoDeDefiniciones() {}

    private static Map.Entry<String, DefinicionDeIndicador> definicion(
            String codigo, FamiliaDeIndicador familia, String dueno, SentidoDeMeta sentido) {
        return Map.entry(codigo, new DefinicionDeIndicador(codigo, familia, dueno, sentido, "v1"));
    }

    public static Optional<DefinicionDeIndicador> de(String codigo) {
        return Optional.ofNullable(POR_CODIGO.get(codigo));
    }

    public static int cuantas() {
        return POR_CODIGO.size();
    }
}
