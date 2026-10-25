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

```ts
import { api, getErrorMessage } from '@/lib/api'

const { data } = await api.get('/establecimientos') // → GET {VITE_API_URL}/api/establecimientos
```

El token se agrega solo en cada request. Los errores del backend llegan con el formato estándar (`message`, `fieldErrors`); `getErrorMessage(error)` devuelve el texto para mostrar.

## Colores

Los colores de marca son tokens en `src/index.css` (`--color-brand-*`) y se usan como `bg-brand-600`, `text-brand-700`, etc. Cuando se defina la identidad, se cambian ahí.
