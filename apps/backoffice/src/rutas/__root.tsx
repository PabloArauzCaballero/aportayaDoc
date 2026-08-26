import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Link, Outlet, createRootRoute } from '@tanstack/react-router'
import '../tokens/andamiaje.css'

/**
 * Raiz del backoffice. TanStack Router arma el arbol **desde los archivos de esta
 * carpeta**: agregar una pantalla no toca ningun registro compartido, que es lo que
 * hace que dos carriles no colisionen (planes/16 §4).
 */
const cliente = new QueryClient({
  defaultOptions: {
    queries: {
      // Sobre dinero no hay reintento silencioso: el reintento lo pide la persona
      // y se ve en pantalla.
      retry: false,
      refetchOnWindowFocus: false,
    },
  },
})

export const Route = createRootRoute({
  component: () => (
    <QueryClientProvider client={cliente}>
      <header
        style={{
          borderBottom: '1px solid var(--color-borde)',
          padding: 'var(--espacio-md)',
          display: 'flex',
          gap: 'var(--espacio-md)',
          alignItems: 'baseline',
        }}
      >
        <strong>AportaYa · backoffice</strong>
        <nav aria-label="Secciones">
          <Link to="/">Inicio</Link>
        </nav>
      </header>
      <main style={{ padding: 'var(--espacio-lg)' }}>
        <Outlet />
      </main>
    </QueryClientProvider>
  ),
})
