import { useState } from 'react'
import type { Indicador } from 'clientes/typescript/auditoria/models'
import { Chispa } from '../atomos/Chispa'
import { Dialogo } from '../moleculas/Dialogo'
import { definicionDe } from '../dominio/definicionesDeIndicador'
import { motivoDeSupresion, semaforoDe, valorLegible, variacionLegible } from '../dominio/formato'

/**
 * Una tarjeta del tablero: el número, su meta, su serie y **dos afordancias**.
 *
 * - **Ampliar** — la misma tarjeta a pantalla completa, con la serie grande y los
 *   valores período a período. Un gráfico de 44 píxeles sirve para ver la tendencia;
 *   no sirve para discutir un número en una reunión.
 * - **Qué es esto** — la definición: qué mide, cómo se calcula, de dónde sale el dato
 *   y quién responde por él. Es lo que evita la discusión sobre de dónde salió la
 *   cifra, que es la discusión que se lleva la reunión entera.
 *
 * Las dos son **botones** y no íconos con `onClick`: se alcanzan con el teclado, se
 * anuncian con su nombre y abren un diálogo del que se sale con `Escape`.
 */
export function TarjetaDeIndicador({ indicador, provisorio }: { indicador: Indicador; provisorio: boolean }) {
  const [ampliada, setAmpliada] = useState(false)
  const [explicando, setExplicando] = useState(false)

  const definicion = definicionDe(indicador.codigo)
  const semaforo = semaforoDe(indicador.cumpleMeta)
  const enRojo = semaforo === 'NO_CUMPLE'
  const variacion = variacionLegible(indicador.variacionPeriodoAnterior)
  const serie = indicador.serie ?? []

  return (
    <article
      style={{
        border: '1px solid var(--color-borde)',
        borderLeft: `3px solid ${enRojo ? 'var(--color-error)' : 'var(--color-acento)'}`,
        borderRadius: 'var(--radio-md)',
        padding: 'var(--espacio-md)',
        display: 'grid',
        gap: 'var(--espacio-sm)',
      }}
    >
      <header style={{ display: 'flex', alignItems: 'start', justifyContent: 'space-between', gap: 'var(--espacio-sm)' }}>
        <h3 style={{ margin: 0, fontSize: 'var(--tipo-cuerpo)' }}>{indicador.nombre}</h3>
        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
          <button
            type="button"
            onClick={() => setAmpliada(true)}
            aria-label={`Ampliar ${indicador.nombre} a pantalla completa`}
            title="Ampliar"
            style={{ minHeight: 'var(--area-tactil)', minWidth: 'var(--area-tactil)' }}
          >
            ⤢
          </button>
          <button
            type="button"
            onClick={() => setExplicando(true)}
            aria-label={`Qué es ${indicador.nombre} y de dónde sale`}
            title="Qué es este indicador"
            style={{ minHeight: 'var(--area-tactil)', minWidth: 'var(--area-tactil)' }}
          >
            i
          </button>
        </div>
      </header>

      {indicador.suprimidoPorPrivacidad ? (
        // El hueco se explica. Un valor ausente sin motivo se lee como una falla del
        // sistema, y alguien abre una incidencia por algo que funciona como debe.
        <p style={{ color: 'var(--color-texto-suave)', margin: 0, fontSize: 'var(--tipo-pie)' }}>
          {motivoDeSupresion(indicador.casos)}
        </p>
      ) : (
        <p
          style={{
            fontSize: 'var(--tipo-monto)',
            fontWeight: 700,
            margin: 0,
            fontVariantNumeric: 'tabular-nums',
            color: enRojo ? 'var(--color-error)' : 'var(--color-texto)',
          }}
        >
          {valorLegible(indicador.valor, indicador.unidad, indicador.moneda)}
        </p>
      )}

      <p style={{ margin: 0, fontSize: 'var(--tipo-pie)', color: 'var(--color-texto-suave)' }}>
        {indicador.meta ? (
          <>
            Meta {valorLegible(indicador.meta, indicador.unidad, indicador.moneda)} ·{' '}
            {semaforo === 'CUMPLE' ? 'cumple' : semaforo === 'NO_CUMPLE' ? 'no cumple' : 'sin meta del período'}
          </>
        ) : (
          'Sin meta fijada para el período: no hay semáforo.'
        )}{' '}
        · {variacion.texto}
      </p>

      {serie.length > 0 ? <Chispa serie={serie} nombre={indicador.nombre} enRojo={enRojo} /> : null}

      {provisorio ? (
        // Se muestra SIEMPRE mientras el período no cuadre. Un número sobre datos sin
        // cuadrar es una opinión, y publicarlo sin la marca es la forma más fácil de
        // que alguien decida con él.
        <p style={{ margin: 0, fontSize: 'var(--tipo-pie)', color: 'var(--color-texto-suave)' }}>
          <strong>Provisorio</strong> — el período todavía no cuadró.
        </p>
      ) : null}

      <Dialogo
        abierto={ampliada}
        alCerrar={() => setAmpliada(false)}
        titulo={indicador.nombre}
        pantallaCompleta
      >
        <p style={{ fontSize: '2.5rem', fontWeight: 700, margin: 0, fontVariantNumeric: 'tabular-nums' }}>
          {indicador.suprimidoPorPrivacidad ? '—' : valorLegible(indicador.valor, indicador.unidad, indicador.moneda)}
        </p>
        {serie.length > 0 ? (
          <>
            <Chispa serie={serie} nombre={indicador.nombre} enRojo={enRojo} alto={220} />
            <h3>Período a período</h3>
            <table style={{ borderCollapse: 'collapse', width: '100%', maxWidth: '32rem' }}>
              <caption style={{ textAlign: 'left', color: 'var(--color-texto-suave)', fontSize: 'var(--tipo-pie)' }}>
                La serie que dibuja el gráfico, en números: un gráfico no se puede citar en un acta.
              </caption>
              <thead>
                <tr>
                  <th scope="col" style={{ textAlign: 'left', borderBottom: '1px solid var(--color-borde)' }}>
                    Período
                  </th>
                  <th scope="col" style={{ textAlign: 'right', borderBottom: '1px solid var(--color-borde)' }}>
                    Valor
                  </th>
                </tr>
              </thead>
              <tbody>
                {serie.map((punto) => (
                  <tr key={punto.periodo}>
                    <td style={{ borderBottom: '1px solid var(--color-borde)' }}>{punto.periodo}</td>
                    <td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', borderBottom: '1px solid var(--color-borde)' }}>
                      {valorLegible(punto.valor, indicador.unidad, indicador.moneda)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ) : (
          <p style={{ color: 'var(--color-texto-suave)' }}>Este indicador todavía no tiene serie publicada.</p>
        )}
        {indicador.explicacion ? (
          <>
            <h3>Explicación del dueño</h3>
            <p>{indicador.explicacion}</p>
          </>
        ) : enRojo ? (
          <p style={{ color: 'var(--color-texto-suave)' }}>
            No cumple la meta y <strong>todavía no tiene explicación del dueño</strong>. Queda pendiente para la
            sesión de comité.
          </p>
        ) : null}
      </Dialogo>

      <Dialogo abierto={explicando} alCerrar={() => setExplicando(false)} titulo={`Qué es ${indicador.nombre}`}>
        {definicion ? (
          <dl style={{ display: 'grid', gap: 'var(--espacio-md)', margin: 0 }}>
            <div>
              <dt style={{ fontWeight: 700 }}>Qué mide</dt>
              <dd style={{ margin: 0 }}>{definicion.queMide}</dd>
            </div>
            <div>
              <dt style={{ fontWeight: 700 }}>Cómo se calcula</dt>
              <dd style={{ margin: 0 }}>{definicion.comoSeCalcula}</dd>
            </div>
            <div>
              <dt style={{ fontWeight: 700 }}>De dónde sale el dato</dt>
              <dd style={{ margin: 0 }}>{definicion.fuente}</dd>
            </div>
            {definicion.advertencia ? (
              <div>
                <dt style={{ fontWeight: 700 }}>Para no leerlo mal</dt>
                <dd style={{ margin: 0 }}>{definicion.advertencia}</dd>
              </div>
            ) : null}
            <div>
              <dt style={{ fontWeight: 700 }}>Quién responde</dt>
              <dd style={{ margin: 0 }}>
                {definicion.duenoFamilia} · familia {definicion.familia}
              </dd>
            </div>
            <div>
              <dt style={{ fontWeight: 700 }}>Versión de la definición</dt>
              <dd style={{ margin: 0 }}>
                {indicador.definicionVersion} — el valor guarda con qué definición se calculó, para que un número
                de hace un año vuelva a salir igual.
              </dd>
            </div>
            {!definicion.revisadaPorDueno ? (
              <p role="note" style={{ color: 'var(--color-error)', margin: 0 }}>
                <strong>Pendiente de revisión.</strong> Esta redacción todavía no la confirmó {definicion.duenoFamilia}.
                Hasta que lo haga, tomala como propuesta y no como la definición oficial.
              </p>
            ) : null}
          </dl>
        ) : (
          <p role="note">
            <strong>Este indicador no tiene definición escrita en el catálogo.</strong> El valor se muestra igual —
            esconderlo dejaría el tablero incompleto sin que se note—, pero hasta que alguien escriba qué mide y de
            dónde sale, no debería usarse para decidir.
          </p>
        )}
      </Dialogo>
    </article>
  )
}
