/**
 * Qué es cada indicador y de dónde sale.
 *
 * **Un indicador es una definición, no una consulta.** Si dos lugares del sistema lo
 * recalculan, ya hay dos indicadores (`indicadores-tablero`). El backend calcula el
 * valor contra la réplica de lectura; este catálogo es lo que el tablero **muestra**
 * cuando alguien pregunta «¿y esto qué es?».
 *
 * > [!warning] Redactado por la máquina, pendiente de revisión
 * > La fórmula y la fuente de cada entrada se escribieron leyendo la bóveda —el caso
 * > de uso que produce el dato y la tabla donde queda—, no dictadas por el dueño de
 * > la familia. **Cada dueño tiene que confirmar la suya antes de que el tablero se
 * > publique**, y hasta entonces `revisadaPorDueno` es `false` y la interfaz lo dice.
 * > Un tablero con una fuente mal atribuida es peor que uno sin explicación: da
 * > confianza donde no corresponde.
 */
export type Familia = 'NEGOCIO' | 'RIESGO' | 'CUMPLIMIENTO' | 'OPERACION' | 'FINANZAS'

export type DefinicionDeIndicador = {
  codigo: string
  /** Qué mide, en una frase, sin fórmula. */
  queMide: string
  /** Cómo se calcula. Es la definición: si esto cambia, se versiona y se avisa. */
  comoSeCalcula: string
  /** De dónde sale el dato: el caso de uso que lo produce y la tabla donde queda. */
  fuente: string
  /** Quién responde cuando está en rojo. Es un rol, no una persona: las personas rotan. */
  duenoFamilia: string
  familia: Familia
  /** Lo que hay que saber para no leerlo mal. */
  advertencia?: string
  /** Falso mientras el dueño de la familia no haya confirmado la redacción. */
  revisadaPorDueno: boolean
}

/**
 * El catálogo, indexado por código. Vive en el frontend **solo como texto
 * explicativo**: el valor, la meta y la variación los calcula `auditoria` (CU-98).
 * Acá no se calcula nada — hacerlo sería crear el segundo indicador.
 */
export const DEFINICIONES: Record<string, DefinicionDeIndicador> = {
  GRUPOS_ACTIVOS: {
    codigo: 'GRUPOS_ACTIVOS',
    familia: 'NEGOCIO',
    queMide: 'Cuántos grupos están corriendo su ciclo en el período.',
    comoSeCalcula:
      'Cantidad de grupos en estado EN_CURSO al cierre del período. No incluye los que están conformándose ni los disueltos, aunque hayan tenido movimiento durante el período.',
    fuente: 'grupos.grupo · estado al cierre. Lo produce el ciclo de vida del grupo (CU-20 a CU-67).',
    duenoFamilia: 'Gerencia comercial',
    advertencia:
      'Un grupo suspendido por disolución en curso deja de contar el día que pasa a SUSPENDIDO, no cuando termina la liquidación.',
    revisadaPorDueno: false,
  },
  VOLUMEN_APORTADO: {
    codigo: 'VOLUMEN_APORTADO',
    familia: 'NEGOCIO',
    queMide: 'Cuánto dinero entró efectivamente a las bolsas en el período.',
    comoSeCalcula:
      'Suma de los aportes acreditados del período. Se toma del libro contable, no de las obligaciones: una obligación marcada como pagada sin asiento no es dinero que entró.',
    fuente:
      'nucleo_financiero.asiento_contable · movimientos de aporte del período (CU-21, CU-24). El cuadre lo garantiza el cierre diario (CU-51).',
    duenoFamilia: 'Gerencia comercial',
    advertencia:
      'Es provisorio mientras el período no esté cuadrado: un aporte conciliado tarde lo mueve hacia arriba después de publicado.',
    revisadaPorDueno: false,
  },
  TASA_DE_MOROSIDAD: {
    codigo: 'TASA_DE_MOROSIDAD',
    familia: 'RIESGO',
    queMide: 'Qué proporción de lo que había que aportar no se aportó en término.',
    comoSeCalcula:
      'Obligaciones vencidas e impagas al cierre, sobre el total de obligaciones exigibles del período. Se cuenta por obligación y no por participante: alguien que debe tres períodos pesa tres veces, que es lo que efectivamente falta en las bolsas.',
    fuente: 'aportes.obligacion_aporte · estado y fecha de vencimiento (CU-19, CU-21).',
    advertencia:
      'No es lo mismo que incumplimiento: una obligación en mora dentro del plazo de gracia todavía no es un incumplimiento declarado (CU-25).',
    duenoFamilia: 'Gerencia de riesgos',
    revisadaPorDueno: false,
  },
  COBERTURAS_CONSUMIDAS: {
    codigo: 'COBERTURAS_CONSUMIDAS',
    familia: 'RIESGO',
    queMide: 'Cuánto tuvo que poner el fondo de garantía para que los grupos no se frenaran.',
    comoSeCalcula:
      'Suma de las coberturas del período, netas de lo repuesto por subrogación o abono en el mismo período.',
    fuente:
      'garantia.cobertura_incumplimiento y garantia.movimiento_fondo · producidos por CU-23; la reposición, por CU-26 y los abonos de cobranza.',
    advertencia:
      'Que baje no siempre es bueno: también baja cuando el fondo se quedó sin saldo y las entregas se bloquearon en vez de cubrirse.',
    duenoFamilia: 'Gerencia de riesgos',
    revisadaPorDueno: false,
  },
  ALERTAS_ABIERTAS: {
    codigo: 'ALERTAS_ABIERTAS',
    familia: 'RIESGO',
    queMide: 'Cuántas alertas tempranas siguen sin desenlace registrado.',
    comoSeCalcula:
      'Alertas creadas y no cerradas al corte. Toda alerta se cierra con resultado —se regularizó o se materializó en incumplimiento—, así que una alerta vieja y abierta es trabajo sin hacer, no ruido.',
    fuente: 'garantia.alerta_temprana · producida por CU-97.',
    advertencia:
      'Este indicador mide el acompañamiento, no el riesgo. Una alerta abierta no habilita ninguna restricción: eso exige causa consumada (CU-27).',
    duenoFamilia: 'Gerencia de riesgos',
    revisadaPorDueno: false,
  },
  REPORTES_EN_PLAZO: {
    codigo: 'REPORTES_EN_PLAZO',
    familia: 'CUMPLIMIENTO',
    queMide: 'Qué proporción de los reportes regulatorios salió dentro del plazo legal.',
    comoSeCalcula:
      'Reportes remitidos antes de su fecha límite sobre el total exigible del período. El plazo es el que se guardó al abrir el reporte, no el que se recalcularía hoy.',
    fuente: 'cumplimiento · reportes a la UIF y a ASFI (CU-42, CU-43, CU-44).',
    advertencia:
      'Cualquier valor por debajo del 100 % es un incumplimiento regulatorio, no una métrica a optimizar. No admite meta menor a 100.',
    duenoFamilia: 'Oficial de cumplimiento',
    revisadaPorDueno: false,
  },
  CIERRES_CUADRADOS: {
    codigo: 'CIERRES_CUADRADOS',
    familia: 'OPERACION',
    queMide: 'Cuántos cierres diarios cuadraron sin intervención manual.',
    comoSeCalcula:
      'Cierres del período sin descuadre de custodia abierto, sobre el total de cierres del período.',
    fuente: 'nucleo_financiero.cierre_diario y nucleo_financiero.descuadre_custodia · producidos por CU-51.',
    advertencia:
      'Es el indicador que sostiene a todos los demás: mientras esté por debajo del 100 %, **el resto del tablero es provisorio** aunque el período figure cerrado.',
    duenoFamilia: 'Gerencia de operaciones',
    revisadaPorDueno: false,
  },
  INGRESOS_DEVENGADOS: {
    codigo: 'INGRESOS_DEVENGADOS',
    familia: 'FINANZAS',
    queMide: 'Cuánto se ganó en el período, se haya cobrado o no.',
    comoSeCalcula:
      'Suma de las comisiones devengadas del período según el tarifario vigente al momento de cada operación, no el de hoy.',
    fuente: 'tarifas · devengo de comisiones (CU-30 a CU-35); el asiento, en nucleo_financiero.asiento_contable.',
    advertencia:
      'Devengado no es cobrado. La diferencia entre este indicador y el de cobrado es exactamente lo que está pendiente de cobro.',
    duenoFamilia: 'Gerencia de finanzas',
    revisadaPorDueno: false,
  },
}

/** La definición, o `undefined` si el backend manda un código que el catálogo no conoce. */
export function definicionDe(codigo: string): DefinicionDeIndicador | undefined {
  return DEFINICIONES[codigo]
}

/**
 * Los códigos que el catálogo no conoce **no se ocultan**: la tarjeta muestra el
 * valor y dice que falta su definición. Esconder el indicador dejaría a la dirección
 * mirando un tablero incompleto sin saberlo; mostrarlo sin explicación, al menos, se
 * ve.
 */
export function sinDefinicion(codigos: string[]): string[] {
  return codigos.filter((c) => !(c in DEFINICIONES))
}
