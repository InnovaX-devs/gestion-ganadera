# 0002 — Multi-tenant con columna `organizacion_id` y `@TenantId`

**Estado:** aceptada · **Fecha:** 2026-10-25 · **Issue:** #8

## Contexto

Cada productor o empresa es una cuenta (organización) y no puede ver datos de otra. El riesgo típico es que alguien olvide filtrar por organización en una consulta y se filtren datos entre cuentas.

## Opciones

1. **Una base o un schema por organización.** Aislamiento fuerte, pero complica migraciones, backups y costos con muchas cuentas chicas.
2. **Columna `organizacion_id` filtrada a mano en cada consulta.** Simple, pero depende de que nadie se olvide nunca.
3. **Columna `organizacion_id` con `@TenantId` de Hibernate 6.** Una sola base; Hibernate completa la columna al insertar y filtra todas las consultas automáticamente.

## Decisión

Opción 3.

- Toda entidad de negocio extiende `TenantEntity`.
- La organización actual sale del usuario autenticado (`AuthenticatedUser` en el `SecurityContext`), nunca del request.
- Sin usuario autenticado, el resolver devuelve un UUID de ceros: no se ve nada y los inserts fallan por la foreign key.
- `TenantIsolationTest` verifica el aislamiento y tiene que seguir pasando siempre.

## Consecuencias

- El código de negocio no menciona la organización: menos errores y menos código.
- Las queries nativas no pasan por el filtro: se evitan o se testean aparte.
- Procesos sin usuario (jobs programados) fijan la organización explícitamente con `TenantContext.comoOrganizacion`: ver [0003](0003-jobs-multi-tenant.md).

### Actualización (#12): rutas públicas con organización fijada

El link de una invitación se abre sin sesión, así que no hay organización de dónde sacar el tenant. El token se busca con una query JDBC (que no pasa por el filtro; es seguro porque el token es la credencial) y, con la organización de la invitación ya conocida, la escritura se hace dentro de `TenantContext.comoOrganizacion(orgId, ...)`. Eso fija la organización para el hilo actual. Hay que llamarlo **antes** de entrar a un método `@Transactional`, porque Hibernate resuelve el tenant al abrir la sesión.
