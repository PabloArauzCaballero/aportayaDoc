import * as matchers from '@testing-library/jest-dom/matchers'
import { cleanup } from '@testing-library/react'
import { afterEach, expect } from 'vitest'

// Los matchers se registran a mano y no por el atajo `/vitest`: el atajo depende de
// que `expect` global ya exista cuando se importa, y con proyectos de Vitest ese
// orden no esta garantizado. Es el mismo hallazgo de `apps/backoffice`.
expect.extend(matchers)

// El sistema de diseno no habla con la red: no hay servidor simulado que levantar.
// Si algun dia un atomo necesitara uno, el atomo esta mal.
afterEach(cleanup)
