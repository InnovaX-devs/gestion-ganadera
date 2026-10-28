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

Para saber quién está logueado desde un servicio: `TenantContext.requerirUsuario()` (devuelve `userId`, `organizacionId` y `email`).

Reglas:

- `Organizacion` y `Usuario` son las únicas que **no** extienden `TenantEntity` (el login busca usuarios antes de saber su organización).
- Usar `TIMESTAMP WITH TIME ZONE` en las migraciones (funciona en PostgreSQL y en el H2 de los tests).
- Las queries nativas (`nativeQuery = true`) **no** se filtran solas: evitarlas, o agregar el filtro y un test.

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
