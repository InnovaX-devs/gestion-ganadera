# 0001 — Stack tecnológico

**Estado:** aceptada · **Fecha:** 2026-10-25

## Contexto

Necesitamos un sistema web multi-usuario y multi-establecimiento, con una app móvil que funcione sin conexión más adelante. El equipo ya trabaja con Java, React y PostgreSQL.

## Decisión

| Parte | Tecnología |
|-------|-----------|
| Backend | Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA |
| Base de datos | PostgreSQL 16, migraciones con Flyway |
| Web | React 19, TypeScript, Vite, Tailwind CSS 4, React Router |
| Infra local | Docker Compose |
| CI | GitHub Actions |
| Móvil | A definir en M5 (React Native/Expo o PWA) |

Organización: monorepo con `/backend`, `/web`, `/mobile` y `/docs`.

Convenciones de datos:

- Claves primarias UUID (permiten generar IDs en el cliente para la sincronización offline).
- Todas las tablas de negocio llevan `organizacion_id` (multi-tenant).
- Fechas en UTC en la base (`timestamptz`).

## Consecuencias

- Un solo repo simplifica PRs que tocan backend y web a la vez.
- Usar UUID desde el inicio evita una migración costosa cuando llegue la app offline.
