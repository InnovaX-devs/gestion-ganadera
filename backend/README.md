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
