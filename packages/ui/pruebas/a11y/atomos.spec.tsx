import { render } from '@testing-library/react'
import { axe } from 'jest-axe'
import { describe, expect, it } from 'vitest'
import { AreaDeTexto } from '../../src/web/AreaDeTexto'
import { Avatar, GrupoDeAvatares } from '../../src/web/Avatar'
import { Boton } from '../../src/web/Boton'
import { Campo } from '../../src/web/Campo'
import { CampoContrasena } from '../../src/web/CampoContrasena'
import { CampoMonto } from '../../src/web/CampoMonto'
import { CampoOTP } from '../../src/web/CampoOTP'
import { Casilla, Opcion } from '../../src/web/Casilla'
import { Chip } from '../../src/web/Chip'
import { ChipEstado } from '../../src/web/ChipEstado'
import { Esqueleto } from '../../src/web/Esqueleto'
import { Girador } from '../../src/web/Girador'
import { Interruptor } from '../../src/web/Interruptor'
import { Monto } from '../../src/web/Monto'
import { Paso } from '../../src/web/Paso'
import { AnilloDeProgreso, BarraDeProgreso } from '../../src/web/Progreso'
import { Puntos } from '../../src/web/Puntos'
import { Seleccion } from '../../src/web/Seleccion'
import { SelectorSegmentado } from '../../src/web/SelectorSegmentado'
import { Tooltip } from '../../src/web/Tooltip'

/**
 * El barrido de accesibilidad sobre **todos** los átomos a la vez.
 *
 * Uno por uno pasa cualquier cosa; juntos aparecen los choques de verdad: un `id`
 * repetido, un `aria-hidden` sobre algo que se puede enfocar, dos controles con el
 * mismo nombre. Por eso se renderizan en una sola página.
 */
describe('accesibilidad de los átomos', () => {
  it('sin violaciones, con todas las piezas en la misma página', async () => {
    const { container } = render(
      <main>
        <h1>Catálogo</h1>

        <Monto valor={{ monto: '1240.00', moneda: 'BOB' }} etiqueta="Saldo" tamano="titular" />
        <Monto valor={{ monto: '-80.00', moneda: 'BOB' }} etiqueta="Comisión" sentido="sale" />

        <Boton variante="primario">Aportar</Boton>
        <Boton variante="secundario" cargando>
          Enviando
        </Boton>
        <Boton variante="fantasma" disabled>
          Cancelar
        </Boton>
        <Boton variante="peligro">Disolver</Boton>
        <Boton variante="enlace">Ver el reglamento</Boton>
        <Boton variante="icono" aria-label="Editar el grupo">
          ✎
        </Boton>
        <Boton variante="fab" aria-label="Nuevo aporte">
          +
        </Boton>

        <label htmlFor="nombre">Nombre del pasanaku</label>
        <Campo id="nombre" ayuda="Como lo van a ver los demás" />
        <label htmlFor="ci">Documento</label>
        <Campo id="ci" estado="error" ayuda="Ese número ya está registrado" />
        <label htmlFor="cuota">Cuota</label>
        <CampoMonto id="cuota" />
        <label htmlFor="clave">Contraseña</label>
        <CampoContrasena id="clave" />
        <label htmlFor="motivo">Motivo</label>
        <AreaDeTexto id="motivo" />
        <label htmlFor="frecuencia">Frecuencia</label>
        <Seleccion id="frecuencia">
          <option value="MENSUAL">Mensual</option>
          <option value="QUINCENAL">Quincenal</option>
        </Seleccion>
        <Paso valor={6} alCambiar={() => {}} minimo={2} maximo={12} etiqueta="Participantes" />
        <CampoOTP valor="12" alCambiar={() => {}} digitos={6} etiqueta="Código de verificación" />

        <Casilla name="acepto">Acepto el reglamento</Casilla>
        <Opcion name="turno">Sorteo</Opcion>
        <Opcion name="turno">Orden de llegada</Opcion>
        <Interruptor encendido alCambiar={() => {}} etiqueta="Avisos por correo" />
        <SelectorSegmentado
          opciones={[
            { valor: 'todos', etiqueta: 'Todos' },
            { valor: 'entradas', etiqueta: 'Entradas' },
          ]}
          valor="todos"
          alCambiar={() => {}}
          etiqueta="Filtrar movimientos"
        />
        <Chip activo alQuitar={() => {}}>
          Vencidos
        </Chip>

        <ChipEstado tono="ok">Al día</ChipEstado>
        <ChipEstado tono="error">En mora</ChipEstado>
        <GrupoDeAvatares restantes={7}>
          <Avatar nombre="Rosa Mamani" tamano={30} />
          <Avatar nombre="Julio Quispe" tamano={30} />
        </GrupoDeAvatares>
        <Girador etiqueta="Buscando el grupo" />
        <BarraDeProgreso porcentaje={70} etiqueta="Fondo juntado" />
        <AnilloDeProgreso porcentaje={45} etiqueta="Avance del período" />
        <Esqueleto ancho={180} />
        <Tooltip texto="La cuota se cobra el primer día hábil">
          <span>Cuota</span>
        </Tooltip>
        <Puntos total={4} actual={1} />
      </main>,
    )
    const resultado = await axe(container)
    expect(resultado.violations).toEqual([])
  })
})
