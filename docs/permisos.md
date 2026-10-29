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
| Ver el establecimiento, lotes, rodeos, animales y equipo | ✅ | ✅ | ✅ | ✅ |
| Editar datos del establecimiento | ✅ | ✅ | ❌ | ❌ |
| Eliminar el establecimiento | ✅ | ❌ | ❌ | ❌ |
| Crear, editar y eliminar lotes y rodeos | ✅ | ✅ | ❌ | ❌ |
| Cargar y editar animales (`GESTIONAR_ANIMALES`) | ✅ | ✅ | ❌ | ❌ |
| Agregar miembros, cambiar roles y quitar miembros | ✅ | ❌ | ❌ | ❌ |
| Registrar eventos sanitarios (tratamientos, tactos, vacunas) \* | ✅ | ✅ | ✅ | ❌ |

\* Permiso `REGISTRAR_SANIDAD`: ya está definido para cada rol, pero se aplica cuando existan esos módulos.

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
- Solo se puede agregar a usuarios que ya pertenecen a la organización. Invitar gente nueva a la organización no está resuelto todavía.
- Cualquier usuario puede crear un establecimiento y queda como su dueño.
- Los establecimientos que existían antes de #11 pasaron a tener como dueños a todos los usuarios de su organización (lo hace la migración `V5`).

## Cómo aplicar permisos en un endpoint nuevo

Al empezar el método del servicio:

```java
autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_AGRUPACIONES);
```

Si el permiso que necesitás no existe, agregalo en `Permiso` y asignalo a los roles en `Rol`. Todo recurso de negocio cuelga de un establecimiento, así que siempre hay un `establecimientoId` contra el cual autorizar.
