# backend

API REST del sistema. Java 21 + Spring Boot 3 + PostgreSQL + Flyway.

## Correr

```bash
# con la base levantada (docker compose up -d desde la raíz)
mvn spring-boot:run
```

- API: http://localhost:8080
- Health: http://localhost:8080/api/health

## Tests

```bash
mvn verify
```

Los tests usan H2 en memoria (perfil `test`), no necesitan Docker.

Helpers en `src/test/java/.../support`:

- `ApiTestClient`: registra cuentas reales y hace requests con token (`api.registrarCuenta("Org A")`, `api.post(token, url, body)`...). Ver `EstablecimientoControllerTest` como modelo.
- `DbCleaner.limpiar(jdbc)`: vacía las tablas en orden. **Al crear una tabla nueva, agregala a su lista.**
- Todo ABM nuevo tiene que tener un test de que otra organización no puede ver, editar ni borrar sus datos.

## Perfiles

| Perfil | Uso | Base |
|--------|-----|------|
| `dev` (default) | Desarrollo local | PostgreSQL de docker compose |
| `test` | Tests automáticos | H2 en memoria |
| `prod` | Producción | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |

Variables de entorno de producción:

| Variable | Descripción |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://host:5432/base` |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales |
| `CORS_ALLOWED_ORIGINS` | URL del frontend, separadas por coma |
| `JWT_SECRET` | Clave para firmar los tokens, **obligatoria**, mínimo 32 caracteres (`openssl rand -base64 48`) |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | Duración de los tokens (default `15m` y `7d`) |
| `PORT` | Puerto HTTP (default 8080) |

## Estructura

```
com.innovax.ganadera
├── config/          Seguridad, CORS y configuración general
├── common/
│   ├── error/       Formato de error estándar y manejo global de excepciones
│   └── health/      GET /api/health
└── <modulo>/        Un paquete por módulo de negocio (establecimiento, animal, evento...)
```

## Autenticación

| Endpoint | Público | Qué hace |
|----------|---------|----------|
| `POST /api/auth/register` | Sí | Crea la organización y su primer usuario. Devuelve tokens (201) |
| `POST /api/auth/login` | Sí | Devuelve tokens. Credenciales inválidas: 401 |
| `POST /api/auth/refresh` | Sí | Recibe `{ "refreshToken": "..." }` y devuelve tokens nuevos |
| `GET /api/auth/me` | No | Usuario logueado y su organización |

Respuesta de login/register/refresh:

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "usuario": { "id": "...", "email": "...", "nombre": "...", "organizacionId": "...", "organizacionNombre": "..." }
}
```

- Todo endpoint no público necesita `Authorization: Bearer <accessToken>`.
- El **access token** dura 15 minutos; el **refresh token**, 7 días. Cada uno lleva un claim `type` y no sirve en lugar del otro.
- Los tokens son JWT firmados con HS256 (Spring Security OAuth2 Resource Server). Traen `sub` (usuario), `org` (organización) y `email`: de ahí sale el `AuthenticatedUser` que usa el filtro multi-tenant.
- Probar a mano:

  ```bash
  curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
    -d '{"nombreOrganizacion":"Mi campo","nombre":"Juan","email":"juan@campo.com","password":"clave-segura-123"}'
  ```

Pendiente para más adelante: invalidar refresh tokens al cerrar sesión o cambiar la contraseña, y limitar intentos de login.

## Multi-tenant (leer antes de crear una entidad)

Cada cuenta es una **organización** y nadie puede ver datos de otra. Esto lo resuelve Hibernate con `@TenantId`, así que **nunca hay que setear ni filtrar `organizacion_id` a mano**.

Para una entidad de negocio nueva (establecimiento, lote, animal, evento...):

1. Que extienda `TenantEntity`, que ya trae `id` (UUID), `organizacionId`, `createdAt` y `updatedAt`:

   ```java
   @Entity
   @Table(name = "establecimiento")
   public class Establecimiento extends TenantEntity {
       private String nombre;
   }
   ```

2. En la migración, la tabla lleva estas columnas:

   ```sql
   CREATE TABLE establecimiento (
       id               UUID                     PRIMARY KEY,
       organizacion_id  UUID                     NOT NULL REFERENCES organizacion (id),
       nombre           VARCHAR(150)             NOT NULL,
       created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
       updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
   );
   CREATE INDEX idx_establecimiento_organizacion ON establecimiento (organizacion_id);
   ```

3. Listo: al guardar se completa la organización del usuario logueado, y todo `findAll`, `findById`, `count` o consulta derivada filtra solo por ella.

## Roles y permisos (leer antes de crear un endpoint)

Cada usuario tiene un rol por establecimiento (`DUENO`, `ENCARGADO`, `VETERINARIO`, `SOCIO`). La matriz está en [`docs/permisos.md`](../docs/permisos.md). **Todo endpoint de negocio tiene que autorizar**: al empezar el método del servicio,

```java
autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_AGRUPACIONES);
```

- Sin membresía en ese establecimiento: 404. Con membresía pero sin el permiso: 403.
- Todo ABM nuevo tiene que tener un test por rol (ver `MembresiaControllerTest` como modelo).

Equipo del establecimiento (`/api/establecimientos/{id}/miembros`): `GET` lista, `POST {email, rol}` agrega, `PUT /{membresiaId} {rol}` cambia el rol y `DELETE /{membresiaId}` quita. Solo el dueño puede modificar.

Invitaciones (`/api/establecimientos/{id}/invitaciones`, solo dueño): `GET` lista, `POST {email, rol}` invita (201), `POST /{id}/reenviar` genera un link nuevo y extiende el plazo, `DELETE /{id}` revoca. El invitado usa los endpoints públicos `GET /api/invitaciones/{token}`, `POST /api/invitaciones/{token}/registro` y, si ya tiene cuenta, `POST /api/invitaciones/{token}/aceptar` (con sesión). Detalle de reglas en [`docs/permisos.md`](../docs/permisos.md).

**Emails.** Con `MAIL_ENABLED=false` (por defecto, dev y tests) no se envía nada: el email se imprime en el log del backend, con el link de la invitación. En producción hay que definir `MAIL_ENABLED=true`, `MAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` y `APP_WEB_URL` (URL pública del frontend, para armar el link). `INVITACION_TTL` (por defecto `7d`) controla la validez. El email se manda después de confirmar la transacción y si falla no rompe la invitación: el dueño puede reenviarla.

Para saber quién está logueado desde un servicio: `TenantContext.requerirUsuario()` (devuelve `userId`, `organizacionId` y `email`).

Reglas:

- `Organizacion` y `Usuario` son las únicas que **no** extienden `TenantEntity` (el login busca usuarios antes de saber su organización).
- Usar `TIMESTAMP WITH TIME ZONE` en las migraciones (funciona en PostgreSQL y en el H2 de los tests).
- Las queries nativas (`nativeQuery = true`) **no** se filtran solas: evitarlas, o agregar el filtro y un test.

## Eventos (nacimiento, pesaje, tratamiento...)

Todos los eventos pasan por un mismo endpoint y una misma tabla (`evento`):

```
POST /api/establecimientos/{id}/eventos
{ "tipo": "OBSERVACION", "fecha": "2026-10-29",
  "animalIds": ["..."],          // o "loteId", o nada (evento del establecimiento)
  "datos": { "texto": "..." } }  // lo propio de cada tipo
GET  /api/establecimientos/{id}/eventos?tipo=&animalId=&loteId=&desde=&hasta=
DELETE /api/establecimientos/{id}/eventos/{eventoId}   // anula, no borra
```

- Un evento sobre varios animales crea una fila por animal con el mismo `operacionId`.
- Si el cliente manda `operacionId` y ya existe, devuelve lo registrado sin duplicar (para la app offline).
- `GET ...?animalId=` es el historial del animal.

**Para agregar un tipo** (#18 a #23), sin tocar `EventoService`:

1. Un record con sus datos y validaciones en `evento/tipos` (ej: `PesajeDatos(@Positive BigDecimal kg)`).
2. Un `@Component` que implemente `TipoEventoHandler<PesajeDatos>`: `validar` (reglas propias), `aplicar` (cambios en el animal) y `revertir` (deshacerlos al anular).
3. Tests en un `XxxEventoTest` usando `ApiTestClient`.

`ObservacionHandler` es el ejemplo más simple. El permiso que pide cada tipo está en `TipoEvento`.
## Stock (#25)

`GET /api/stock?establecimientoId=<uuid>`: animales **activos** del establecimiento, por sexo, categoría, lote y rodeo. Lo ve cualquier miembro (`VER`).

- No hay tabla de stock: se calcula en cada request con una sola consulta JPQL agrupada (`StockRepository`), así que nunca se desincroniza ni se edita a mano. Es JPQL y no una vista de la base para que la filtre `@TenantId`. La acompaña el índice `idx_animal_stock` (`V7`).
- `porSexo` y `porCategoria` traen siempre todas las claves (en 0 si no hay).
- `porLote` / `porRodeo`: todos los lotes y rodeos activos, también los vacíos, por nombre. Al final va el grupo **sin** `id` ni `nombre` = animales sin lote (o sin rodeo), solo si hay alguno. La suma de los `total` de cada lista es el `total` general.
- Falta `establecimientoId` o no es un UUID: 400. Establecimiento ajeno, inexistente o dado de baja: 404.

```json
{
  "establecimientoId": "...",
  "total": 354,
  "porSexo": { "HEMBRA": 202, "MACHO": 152 },
  "porCategoria": { "VACA": 127, "VAQUILLONA": 34, "TERNERA": 41, "TERNERO": 41, "NOVILLITO": 38, "NOVILLO": 64, "TORITO": 3, "TORO": 6 },
  "porLote": [
    { "id": "...", "nombre": "Recría 2026", "total": 90, "porSexo": { ... }, "porCategoria": { ... } },
    { "total": 13, "porSexo": { ... }, "porCategoria": { ... } }
  ],
  "porRodeo": [ ... ]
}
```

`StockControllerTest.elStockCoincideConLaListaDeAnimales` verifica que el stock coincida siempre con el listado de animales (`GET /establecimientos/{id}/animales`) y sus filtros.

## Errores

Toda la API responde los errores con este formato:

```json
{
  "timestamp": "2026-10-25T23:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Hay datos inválidos",
  "path": "/api/animales",
  "fieldErrors": [{ "field": "caravana", "message": "no debe estar vacío" }]
}
```

Para cortar un flujo desde un servicio: `throw NotFoundException.of("Animal", id)` (404) o `throw new BusinessException("...")` (422).

## Maven Wrapper (opcional)

Para que nadie necesite Maven instalado, cualquiera puede generar el wrapper una vez y commitearlo:

```bash
mvn -N wrapper:wrapper
```
