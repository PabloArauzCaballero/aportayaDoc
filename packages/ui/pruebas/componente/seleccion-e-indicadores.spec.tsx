import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Avatar, GrupoDeAvatares } from '../../src/web/Avatar'
import { Boton } from '../../src/web/Boton'
import { Chip } from '../../src/web/Chip'
import { Interruptor } from '../../src/web/Interruptor'
import { AnilloDeProgreso, BarraDeProgreso } from '../../src/web/Progreso'
import { Puntos } from '../../src/web/Puntos'
import { SelectorSegmentado } from '../../src/web/SelectorSegmentado'

describe('Interruptor', () => {
  it('es un switch de verdad, con su nombre', () => {
    const cambiar = vi.fn()
    render(<Interruptor encendido={false} alCambiar={cambiar} etiqueta="Avisos por correo" />)
    const control = screen.getByRole('switch', { name: 'Avisos por correo' })
    fireEvent.click(control)
    expect(cambiar).toHaveBeenCalledWith(true)
  })
})

describe('SelectorSegmentado', () => {
  const opciones = [
    { valor: 'todos', etiqueta: 'Todos' },
    { valor: 'entradas', etiqueta: 'Entradas' },
    { valor: 'salidas', etiqueta: 'Salidas' },
  ] as const

  it('se recorre con las flechas, no tabulando por cada opción', () => {
    const cambiar = vi.fn()
    render(
      <SelectorSegmentado opciones={opciones} valor="todos" alCambiar={cambiar} etiqueta="Filtrar movimientos" />,
    )
    fireEvent.keyDown(screen.getByRole('tab', { name: 'Todos' }), { key: 'ArrowRight' })
    expect(cambiar).toHaveBeenCalledWith('entradas')
  })

  it('la flecha izquierda desde la primera da la vuelta', () => {
    const cambiar = vi.fn()
    render(<SelectorSegmentado opciones={opciones} valor="todos" alCambiar={cambiar} etiqueta="Filtrar" />)
    fireEvent.keyDown(screen.getByRole('tab', { name: 'Todos' }), { key: 'ArrowLeft' })
    expect(cambiar).toHaveBeenCalledWith('salidas')
  })

  it('solo la opción activa entra en el orden de tabulación', () => {
    render(<SelectorSegmentado opciones={opciones} valor="entradas" alCambiar={vi.fn()} etiqueta="Filtrar" />)
    expect(screen.getByRole('tab', { name: 'Entradas' })).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('tab', { name: 'Salidas' })).toHaveAttribute('tabindex', '-1')
  })
})

describe('Chip', () => {
  it('la cruz dice qué quita, no «×»', () => {
    const quitar = vi.fn()
    render(
      <Chip activo alQuitar={quitar}>
        Vencidos
      </Chip>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Quitar Vencidos' }))
    expect(quitar).toHaveBeenCalled()
  })

  it('sin `alQuitar` no hay cruz: un filtro fijo no se saca', () => {
    render(<Chip>Este mes</Chip>)
    expect(screen.queryByRole('button', { name: /Quitar/ })).toBeNull()
  })
})

describe('Avatar', () => {
  it('las iniciales son decoración; el nombre completo es lo que se lee', () => {
    render(<Avatar nombre="Rosa Mamani" tamano={40} />)
    // Dos letras no identifican a nadie en una lista de participantes.
    expect(screen.getByText('Rosa Mamani')).toBeInTheDocument()
    expect(screen.getByText('RM')).toHaveAttribute('aria-hidden', 'true')
  })

  it('el mismo nombre da siempre el mismo color', () => {
    const { container: uno } = render(<Avatar nombre="Rosa Mamani" />)
    const { container: otro } = render(<Avatar nombre="Rosa Mamani" />)
    expect(uno.firstElementChild?.className).toBe(otro.firstElementChild?.className)
  })

  it('el resto del grupo se dice con número, no se recorta en silencio', () => {
    render(
      <GrupoDeAvatares restantes={7}>
        <Avatar nombre="Rosa Mamani" tamano={24} />
      </GrupoDeAvatares>,
    )
    expect(screen.getByText('+7')).toBeInTheDocument()
  })
})

describe('Progreso', () => {
  it('la barra informa el valor, y el número está escrito', () => {
    render(<BarraDeProgreso porcentaje={70} etiqueta="Fondo juntado" />)
    expect(screen.getByRole('progressbar', { name: 'Fondo juntado' })).toHaveAttribute('aria-valuenow', '70')
    expect(screen.getByText('70%')).toBeInTheDocument()
  })

  it('un porcentaje fuera de rango se acota en vez de dibujar cualquier cosa', () => {
    render(<AnilloDeProgreso porcentaje={140} etiqueta="Avance" />)
    expect(screen.getByRole('progressbar', { name: 'Avance' })).toHaveAttribute('aria-valuenow', '100')
  })
})

describe('Puntos', () => {
  it('cuatro puntos iguales no se cuentan al oído: van con texto', () => {
    render(<Puntos total={4} actual={1} />)
    expect(screen.getByText('Paso 2 de 4')).toBeInTheDocument()
  })
})

describe('Boton', () => {
  it('un botón de ícono sin nombre no se publica: falla en desarrollo', () => {
    // Es a propósito. Un botón cuyo nombre accesible es «×» no se puede usar sin ver
    // la pantalla, y descubrirlo en producción es tarde.
    expect(() => render(<Boton variante="icono">×</Boton>)).toThrow(/aria-label/)
  })

  it('con nombre, funciona', () => {
    render(
      <Boton variante="fab" aria-label="Nuevo aporte">
        +
      </Boton>,
    )
    expect(screen.getByRole('button', { name: 'Nuevo aporte' })).toBeInTheDocument()
  })
})
