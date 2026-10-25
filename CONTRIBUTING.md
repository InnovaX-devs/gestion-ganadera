# Cómo trabajamos

## Ramas

| Rama | Para qué |
|------|----------|
| `main` | Código estable, lo que está en producción. Solo se actualiza desde `develop`. |
| `develop` | Integración. Todas las features se mergean acá. |
| `feature/<n>-descripcion` | Una rama por issue. Ej: `feature/7-login-jwt` |
| `fix/<n>-descripcion` | Corrección de un bug. Ej: `fix/40-stock-negativo` |

Flujo:

```bash
git checkout develop
git pull
git checkout -b feature/7-login-jwt
# ...trabajás, commiteás...
git push -u origin feature/7-login-jwt
# abrís el PR contra develop
```

- Nadie pushea directo a `main` ni a `develop`.
- Antes de abrir el PR, actualizá tu rama con `develop` y resolvé conflictos.
- Un issue = una rama = un PR. Si el PR crece mucho, partilo.

## Commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/es/):

```
<tipo>(<alcance opcional>): <descripción en minúscula>
```

| Tipo | Uso |
|------|-----|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de bug |
| `refactor` | Cambio de código sin cambiar comportamiento |
| `test` | Tests |
| `docs` | Documentación |
| `style` | Formato, sin cambios de lógica |
| `chore` | Configuración, dependencias, build |
| `ci` | GitHub Actions |

Ejemplos:

```
feat(animales): agregar búsqueda por caravana
fix(stock): no contar animales vendidos
chore(web): actualizar vite
```

## Pull requests

- Título con el mismo formato que los commits.
- En la descripción poné `Closes #<n>` para que el issue se cierre solo al mergear.
- Completá el template (qué cambia, cómo probarlo).
- Al menos **1 aprobación** de otro integrante y **CI en verde** para mergear.
- Mergeamos con **Squash and merge** para que el historial quede limpio.

## Issues

- Usá los templates de *Feature* o *Bug*.
- Asignate el issue antes de empezar, así nadie trabaja en lo mismo.
- Cada issue lleva labels (`backend`, `frontend`...) y un milestone.

## Código

- **Backend:** paquetes por módulo de negocio (`establecimiento`, `animal`, `evento`...), no por capa técnica. DTOs para entrada/salida, nunca exponer entidades JPA.
- **Web:** componentes en PascalCase, hooks con prefijo `use`, una carpeta por página en `src/pages`.
- Corré `npm run lint` y `npm run format` antes de commitear en `/web`.
- Toda migración de base va en un archivo Flyway nuevo (`V2__...sql`); nunca se edita una migración ya mergeada.

## Protección de ramas (configurar en GitHub)

Settings → Branches → Add rule para `main` y `develop`:

- Require a pull request before merging (1 aprobación)
- Require status checks to pass: `Backend` y `Web`
- Do not allow bypassing the above settings
