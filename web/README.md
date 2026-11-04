# web

Panel web. React 19 + TypeScript + Vite + Tailwind CSS 4 + React Router.

## Correr

```bash
cp .env.example .env
npm install
npm run dev        # http://localhost:5173
```

## Scripts

| Script                 | Qué hace                               |
| ---------------------- | -------------------------------------- |
| `npm run dev`          | Servidor de desarrollo                 |
| `npm run build`        | Chequeo de tipos + build de producción |
| `npm run lint`         | Linter (oxlint)                        |
| `npm run format`       | Formatea todo con Prettier             |
| `npm run format:check` | Verifica el formato (lo corre el CI)   |

## Estructura

```
src/
├── components/   Componentes reutilizables
├── config/       navigation.ts (menú lateral)
├── hooks/        Hooks propios (useTheme...)
├── layouts/      AppLayout: sidebar + header
├── lib/          api.ts (cliente HTTP), auth-storage.ts
├── pages/        Una página por sección
├── router.tsx    Rutas
└── index.css     Tailwind y tokens de marca (@theme)
```

## Agregar una página

1. Crear `src/pages/AnimalesPage.tsx`.
2. En `router.tsx`, sacar esa ruta de los placeholders y agregar `{ path: 'animales', element: <AnimalesPage /> }`.
3. Si es una sección nueva del menú, sumarla en `src/config/navigation.ts`.

## Llamar al backend

Para datos del servidor usamos **TanStack Query**: cachea, evita requests repetidos y actualiza todas las pantallas que muestran lo mismo. Cada módulo tiene su `queries.ts` (ver `src/features/establecimientos/queries.ts` como modelo):

```ts
// Leer
const { data, isPending, error } = useEstablecimientos()

// Modificar: al terminar invalida la lista y todas las pantallas se refrescan solas
const crear = useCrearEstablecimiento()
await crear.mutateAsync({ nombre: 'La Esperanza', ... })
```

Por debajo usan `api` (`src/lib/api.ts`), que agrega el token y lo renueva solo cuando vence. Los errores del backend llegan con el formato estándar: `getErrorMessage(error)` da el texto general y `getFieldErrors(error)` los errores por campo.

## Establecimiento activo

Casi todo (lotes, animales, eventos) se muestra para el establecimiento elegido en el header:

```ts
const { activo } = useEstablecimientoActivo()
// activo.id → filtrar los datos de ese campo. Es null si todavía no hay ninguno.
```

## Componentes base

`Button` (variantes primary/secondary/danger y `loading`), `TextField`, `FormError`, `Dialog` (modal) y `ConfirmDialog` (confirmar acciones destructivas) están en `src/components`. Usarlos en vez de crear estilos nuevos.

## Colores

Los colores de marca son tokens en `src/index.css` (`--color-brand-*`) y se usan como `bg-brand-600`, `text-brand-700`, etc. Cuando se defina la identidad, se cambian ahí.
