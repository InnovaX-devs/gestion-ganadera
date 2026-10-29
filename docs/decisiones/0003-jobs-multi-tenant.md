# 0003 — Jobs programados en un sistema multi-tenant

**Estado:** aceptada · **Fecha:** 2026-10-29 · **Issue:** #16

## Contexto

El filtro multi-tenant (ver 0002) toma la organización del usuario logueado. Un job programado
corre sin usuario, así que con el resolver tal como estaba no vería ningún dato. El primer caso es
el job diario de cambio de categoría por edad (#16), y después van a venir otros (cotizaciones,
alertas, recordatorios sanitarios).

## Opciones

1. **Poner un usuario "de sistema" en el `SecurityContext`.** Funciona con el resolver actual, pero
   mezcla autenticación con organización y ese usuario falso podría pasar los chequeos de permisos.
2. **Consultas nativas sin filtro.** Se salta el aislamiento: justo lo que 0002 quería evitar.
3. **Fijar la organización explícitamente durante la tarea**, con un `ThreadLocal` que el resolver
   mira antes que el usuario logueado.

## Decisión

Opción 3: `TenantContext.comoOrganizacion(organizacionId, tarea)`, el mismo mecanismo que se agregó
para aceptar invitaciones sin sesión (#12, ver la actualización de 0002).

- El job recorre las organizaciones y procesa cada una dentro de `comoOrganizacion`, en su propia
  transacción. La transacción tiene que abrirse **dentro** de la tarea: Hibernate fija la
  organización al abrir la sesión.
- La organización siempre sale de un dato confiable de la base (acá, la tabla `organizacion`), nunca
  de un parámetro del request.
- Al terminar la tarea se restaura el valor anterior (no queda "pegado" en el hilo del pool).
- No hay usuario, así que los servicios que llama el job no pasan por `AutorizacionService`.
- Un error en una organización se loguea y no frena a las demás.

Los jobs se activan con `@EnableScheduling` (`SchedulingConfig`) y se apagan con
`app.jobs.habilitados=false`; los tests los apagan y los llaman a mano.

## Consecuencias

- Los jobs usan los mismos repositorios y el mismo filtro que los requests.
- Con más de una instancia del backend, cada una correría el job. Los jobs tienen que ser
  idempotentes (el de categorías lo es: un animal que ya cambió no vuelve a cambiar), y cuando haya
  varias instancias conviene agregar un lock distribuido (por ejemplo, ShedLock).
