import type { IndicadorSerieInner } from 'clientes/typescript/auditoria/models'

/**
 * El gráfico de la tarjeta: la serie del indicador, sin ejes ni leyenda.
 *
 * Es SVG a mano y no una librería de gráficos, por dos razones concretas: el
 * backoffice ya pesa lo suyo y una librería para dibujar ocho puntos son cien
 * kilobytes que se cargan en cada pantalla; y el marcado propio es el único que
 * podemos hacer accesible de verdad.
 *
 * **No es decorativo, y por eso no va con `aria-hidden`.** Lleva su descripción en
 * texto: quien usa lector de pantalla tiene que poder saber si la serie sube o baja,
 * no enterarse de que hay «una imagen».
 */
export type PropiedadesDeChispa = {
  serie: IndicadorSerieInner[]
  /** Alto en píxeles. El ancho lo pone el contenedor. */
  alto?: number
  /** Cómo se llama lo que se está dibujando, para la descripción. */
  nombre: string
  /** Verdadero cuando el indicador no cumple su meta: se dibuja en rojo. */
  enRojo?: boolean
}

export function Chispa({ serie, alto = 44, nombre, enRojo = false }: PropiedadesDeChispa) {
  const puntos = serie.map((p) => Number(p.valor)).filter((n) => Number.isFinite(n))

  // Una serie de un solo punto no es una serie: no hay tendencia que dibujar, y una
  // línea plana afirmaría que se mantuvo estable.
  if (puntos.length < 2) {
    return (
      <p style={{ color: 'var(--color-texto-suave)', fontSize: 'var(--tipo-pie)', margin: 0 }}>
        Sin serie suficiente para el gráfico.
      </p>
    )
  }

  const maximo = Math.max(...puntos)
  const minimo = Math.min(...puntos)
  const rango = maximo - minimo || 1
  const ancho = 100

  const coordenadas = puntos.map((valor, i) => {
    const x = (i / (puntos.length - 1)) * ancho
    // El eje Y del SVG crece hacia abajo: se invierte para que «más» quede arriba.
    const y = alto - ((valor - minimo) / rango) * (alto - 6) - 3
    return `${x.toFixed(2)},${y.toFixed(2)}`
  })

  const primero = puntos[0] ?? 0
  const ultimo = puntos[puntos.length - 1] ?? 0
  const sentido = ultimo > primero ? 'sube' : ultimo < primero ? 'baja' : 'se mantiene'
  const color = enRojo ? 'var(--color-error)' : 'var(--color-acento)'

  return (
    <figure style={{ margin: 0 }}>
      <svg
        viewBox={`0 0 ${ancho} ${alto}`}
        preserveAspectRatio="none"
        role="img"
        aria-label={`Serie de ${nombre}: ${sentido} de ${primero} a ${ultimo} en ${puntos.length} períodos`}
        style={{ width: '100%', height: alto, display: 'block' }}
      >
        <polyline
          points={coordenadas.join(' ')}
          fill="none"
          stroke={color}
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
        {/* El último punto marcado: es el valor del período que se está mirando. */}
        <circle
          cx={ancho}
          cy={Number(coordenadas[coordenadas.length - 1]?.split(',')[1] ?? 0)}
          r={2.5}
          fill={color}
          vectorEffect="non-scaling-stroke"
        />
      </svg>
    </figure>
  )
}
