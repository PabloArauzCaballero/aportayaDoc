package bo.aportaya.cumplimiento;

import bo.aportaya.cumplimiento.aplicacion.CU40EvaluarLimites;
import bo.aportaya.cumplimiento.aplicacion.CU41RegistrarPcc01;
import bo.aportaya.cumplimiento.aplicacion.CU42RegistrarRog;
import bo.aportaya.cumplimiento.aplicacion.CU43RemitirReportes;
import bo.aportaya.cumplimiento.aplicacion.CU44InvestigarYReportar;
import bo.aportaya.cumplimiento.aplicacion.CU45AtenderRequerimiento;
import bo.aportaya.cumplimiento.aplicacion.CU47EvaluarRiesgoDeProducto;
import bo.aportaya.cumplimiento.aplicacion.CU48CalibrarReglas;
import bo.aportaya.cumplimiento.aplicacion.CU49DesignarOficial;
import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo;
import bo.aportaya.cumplimiento.aplicacion.CU53ElevarReclamo;
import bo.aportaya.cumplimiento.aplicacion.CU56EjecutarPruebaDeContinuidad;
import bo.aportaya.cumplimiento.aplicacion.CU94ElevarAlComite;
import bo.aportaya.cumplimiento.infraestructura.CasoLftRepositorio;
import bo.aportaya.cumplimiento.infraestructura.ContinuidadRepositorio;
import bo.aportaya.cumplimiento.infraestructura.EvaluacionProductoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.GobiernoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.LicenciaRepositorio;
import bo.aportaya.cumplimiento.infraestructura.LimiteRepositorio;
import bo.aportaya.cumplimiento.infraestructura.MonitoreoLftRepositorio;
import bo.aportaya.cumplimiento.infraestructura.OperacionRelevanteRepositorio;
import bo.aportaya.cumplimiento.infraestructura.ReclamoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.ReporteRegulatorioRepositorio;
import bo.aportaya.cumplimiento.infraestructura.RequerimientoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.Outbox;

/**
 * El cableado a mano de los doce casos de uso de la ola 3.
 *
 * <p>Vive aparte porque doce constructores con su politica no caben en el mismo metodo
 * sin que deje de verse cual es cual. Es la misma razon por la que el servicio real usa
 * una clase de configuracion y no un constructor gigante.
 */
final class ArmadoDeOla3 {

    private static final String URL_PUBLICA = "https://aportaya.bo";

    /** Dias para comunicar al regulador la designacion del oficial. Es normativo. */
    private static final int DIAS_PARA_COMUNICAR = 5;

    /** Cuanto tiene alguien recien ingresado antes de contar como pendiente. */
    private static final int PLAZO_DE_CAPACITACION = 90;

    /** Minimo de caracteres para que el alcance de un oficio no sea ambiguo. */
    private static final int ALCANCE_MINIMO = 10;

    /** Dias para cerrar el plan de accion de una prueba de continuidad fallida. */
    private static final int DIAS_PARA_REGULARIZAR = 30;

    /** Dias que la norma da al cliente para elevar tras la respuesta. */
    private static final int DIAS_PARA_ELEVAR = 30;

    private ArmadoDeOla3() {}

    static void armar(Datos datos, Outbox outbox) {
        var operaciones = new OperacionRelevanteRepositorio();
        var reportes = new ReporteRegulatorioRepositorio();
        var monitoreo = new MonitoreoLftRepositorio();
        var gobierno = new GobiernoRepositorio();
        var evaluaciones = new EvaluacionProductoRepositorio();
        var reclamos = new ReclamoRepositorio();
        var continuidad = new ContinuidadRepositorio();
        var requerimientos = new RequerimientoRepositorio();

        BaseDeCumplimiento.limiteCU =
                new CU40EvaluarLimites(datos, new LimiteRepositorio(), outbox, Reloj.delSistema());
        BaseDeCumplimiento.pccCU = new CU41RegistrarPcc01(datos, operaciones, outbox, Reloj.delSistema());
        BaseDeCumplimiento.rogCU = new CU42RegistrarRog(datos, operaciones, outbox, Reloj.delSistema());
        BaseDeCumplimiento.reporteCU = new CU43RemitirReportes(
                datos, reportes, operaciones, gobierno, outbox, Reloj.delSistema(), URL_PUBLICA);
        BaseDeCumplimiento.casoCU = new CU44InvestigarYReportar(
                datos,
                monitoreo,
                new CasoLftRepositorio(),
                outbox,
                Reloj.delSistema(),
                PoliticaDeCarril.PLAZOS_DE_CASO);
        BaseDeCumplimiento.oficioCU = new CU45AtenderRequerimiento(
                datos, requerimientos, gobierno, outbox, Reloj.delSistema(), ALCANCE_MINIMO);
        BaseDeCumplimiento.productoCU = new CU47EvaluarRiesgoDeProducto(
                datos,
                evaluaciones,
                new LicenciaRepositorio(),
                outbox,
                Reloj.delSistema(),
                PoliticaDeCarril.ESCALA_DE_RIESGO_DE_PRODUCTO);
        BaseDeCumplimiento.reglaCU =
                new CU48CalibrarReglas(datos, monitoreo, outbox, Reloj.delSistema(), PoliticaDeCarril.TECHO_DE_TRAFICO);
        BaseDeCumplimiento.oficialCU = new CU49DesignarOficial(
                datos,
                gobierno,
                outbox,
                Reloj.delSistema(),
                DIAS_PARA_COMUNICAR,
                PLAZO_DE_CAPACITACION,
                PoliticaDeCarril.ROLES_INCOMPATIBLES);
        BaseDeCumplimiento.reclamoCU = new CU52AtenderReclamo(
                datos,
                reclamos,
                gobierno,
                outbox,
                Reloj.delSistema(),
                PoliticaDeCarril.TODOS_HABILES,
                PoliticaDeCarril.DIAS_DE_RESPUESTA,
                PoliticaDeCarril.MAXIMO_DIAS_DE_PRORROGA);
        BaseDeCumplimiento.instanciaCU =
                new CU53ElevarReclamo(datos, reclamos, outbox, Reloj.delSistema(), URL_PUBLICA, DIAS_PARA_ELEVAR);
        BaseDeCumplimiento.continuidadCU = new CU56EjecutarPruebaDeContinuidad(
                datos, continuidad, gobierno, outbox, Reloj.delSistema(), DIAS_PARA_REGULARIZAR);
        BaseDeCumplimiento.comiteCU =
                new CU94ElevarAlComite(datos, gobierno, evaluaciones, outbox, Reloj.delSistema(), URL_PUBLICA);
    }
}
