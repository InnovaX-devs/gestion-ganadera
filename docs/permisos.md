# Permisos por rol

**Issue:** #11

Cada usuario tiene un rol **por establecimiento** (tabla `membresia`). Puede ser dueño en un campo y socio en otro. Los permisos se definen en `Rol.java`; esta tabla y esa clase tienen que decir lo mismo (los tests de `MembresiaControllerTest` verifican la matriz).

## Roles

| Rol | Quién es |
|-----|----------|
| **Dueño** | Administra el establecimiento y su equipo. Quien lo crea queda como dueño. |
| **Encargado** | Maneja el día a día del campo: edita datos y organiza lotes y rodeos. |
| **Veterinario** | Registra eventos sanitarios. No modifica la estructura del campo. |
| **Socio** | Solo lectura. |

## Matriz

| Acción | Dueño | Encargado | Veterinario | Socio |
|--------|:-----:|:---------:|:-----------:|:-----:|
| Ver el establecimiento, lotes, rodeos, animales, stock y equipo | ✅ | ✅ | ✅ | ✅ |
| Editar datos del establecimiento | ✅ | ✅ | ❌ | ❌ |
| Eliminar el establecimiento | ✅ | ❌ | ❌ | ❌ |
| Crear, editar y eliminar lotes y rodeos | ✅ | ✅ | ❌ | ❌ |
| Cargar y editar animales (`GESTIONAR_ANIMALES`) | ✅ | ✅ | ❌ | ❌ |
| Registrar y anular eventos de manejo: nacimiento, servicio, pesaje, movimiento, venta, baja (`GESTIONAR_ANIMALES`) | ✅ | ✅ | ❌ | ❌ |
| Mover animales de lote, rodeo o establecimiento (movimiento, `GESTIONAR_ANIMALES`). Para mover a otro establecimiento hace falta el permiso **en los dos** | ✅ | ✅ | ❌ | ❌ |
| Ver los pesajes y su ganancia diaria de peso | ✅ | ✅ | ✅ | ✅ |
| Registrar y anular eventos sanitarios: tacto, tratamiento (`REGISTRAR_SANIDAD`) | ✅ | ✅ | ✅ | ❌ |
| Ver el catálogo de productos sanitarios y la carencia de cada animal | ✅ | ✅ | ✅ | ✅ |
| Agregar, editar y dar de baja productos del catálogo sanitario (`REGISTRAR_SANIDAD`) | ✅ | ✅ | ✅ | ❌ |
| Registrar y anular observaciones (cualquiera de los dos permisos) | ✅ | ✅ | ✅ | ❌ |
| Agregar miembros, cambiar roles y quitar miembros | ✅ | ❌ | ❌ | ❌ |
| Invitar por email, ver, reenviar y revocar invitaciones | ✅ | ❌ | ❌ | ❌ |
| Registrar eventos sanitarios (tratamientos, tactos, vacunas) \* | ✅ | ✅ | ✅ | ❌ |

El permiso que pide cada tipo de evento está en `TipoEvento.java`.

Cada issue que agregue endpoints (animales, nacimientos, pesajes, ventas...) tiene que sumar su fila a esta tabla y su permiso a `Permiso`/`Rol`.

## Respuestas de la API

| Situación | Respuesta |
|-----------|-----------|
| No hay token | 401 |
| El establecimiento es de otra organización, no existe o el usuario **no es miembro** | 404 |
| Es miembro pero su rol no tiene el permiso | 403 |

El 404 para quien no es miembro es a propósito: así no se puede averiguar si un id existe.

## Reglas

- Un establecimiento siempre tiene al menos un dueño: no se puede quitar ni degradar al último (422).
- Un usuario tiene un solo rol por establecimiento (409 si se lo vuelve a agregar).
- Agregar un miembro directo (`POST /miembros`) solo sirve para usuarios que ya pertenecen a la organización. Para gente nueva se usa una invitación por email.
- Cualquier usuario puede crear un establecimiento y queda como su dueño.
- Un movimiento solo puede llevar animales a establecimientos de la misma organización. Quien lo registra tiene que poder gestionar animales en el de origen y en el de destino (404 si no es miembro del destino, 403 si no tiene el permiso); anularlo, que devuelve los animales al origen, pide lo mismo.
- Vender o dar de baja animales pide `GESTIONAR_ANIMALES` (igual que anular la venta o la baja, que los devuelve al stock). Vender animales en carencia sanitaria exige confirmarlo en el pedido (`confirmarCarencia`); la baja por muerte o pérdida no.
- Los establecimientos que existían antes de #11 pasaron a tener como dueños a todos los usuarios de su organización (lo hace la migración `V5`).

## Invitaciones por email

El dueño invita a un email eligiendo el establecimiento y el rol (`POST /api/establecimientos/{id}/invitaciones`). Al invitado le llega un link `/invitacion/<token>`.

- El token es aleatorio (256 bits), viaja solo por email y en la base se guarda únicamente su hash SHA-256. La API nunca lo devuelve.
- Vence a los 7 días (`INVITACION_TTL`). Una invitación vencida no se puede usar: el dueño la **reenvía** y se genera un link nuevo (el anterior deja de funcionar).
- Estados: `PENDIENTE`, `ACEPTADA`, `VENCIDA`, `REVOCADA`. El vencimiento no se guarda: se calcula con la fecha.
- Si el invitado **no tiene cuenta**, se registra desde el link (nombre, apellido y contraseña). Queda en la organización del dueño con el rol de la invitación.
- Si **ya tiene cuenta** en esa organización, inicia sesión y acepta. Solo puede aceptar la cuenta cuyo email coincide con el invitado.
- Un email con cuenta en **otra** organización no se puede invitar (409): hoy un usuario pertenece a una sola organización.
- No se puede invitar a un miembro actual ni duplicar una invitación pendiente (409). Las revocadas o vencidas no bloquean una invitación nueva.
- Aceptada no se puede revocar ni reenviar (422). Revocar una ya revocada no hace nada.

Endpoints públicos (el token es la credencial): `GET /api/invitaciones/{token}`, `POST /api/invitaciones/{token}/registro`. `POST /api/invitaciones/{token}/aceptar` requiere sesión.

## Cómo aplicar permisos en un endpoint nuevo

Al empezar el método del servicio:

```java
autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_AGRUPACIONES);
```

Si el permiso que necesitás no existe, agregalo en `Permiso` y asignalo a los roles en `Rol`. Todo recurso de negocio cuelga de un establecimiento, así que siempre hay un `establecimientoId` contra el cual autorizar.
