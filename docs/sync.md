# Sincronización offline (issue #30)

Cómo se sincroniza la app sin conexión con el servidor. La decisión de tecnología está en
[`decisiones/0004-app-movil-offline.md`](decisiones/0004-app-movil-offline.md).

## Resumen

```
POST /api/sync
{
  "establecimientoId": "...",
  "cursor": "...",            // el de la respuesta anterior; sin cursor = desde cero
  "limite": 500,              // opcional, máximo 1000
  "operaciones": [ ... ]      // push: lo hecho sin conexión, en orden (máximo 100)
}
```

Una sola llamada hace las dos cosas: primero aplica las `operaciones` (push) y después devuelve lo
que cambió desde el `cursor` (pull), incluido lo que se acaba de aplicar.

```
{
  "cursor": "...",            // guardarlo para la próxima
  "hayMas": false,            // true: llamar de nuevo con este cursor
  "servidorEn": "2026-10-31T14:05:00Z",
  "resultados": [ { "id": "...", "estado": "APLICADO" }, ... ],
  "establecimiento": { "id": "...", "nombre": "La Esperanza", "rol": "ENCARGADO" },
  "cambios": { "lotes": [], "rodeos": [], "productos": [], "animales": [], "eventos": [] },
  "bajas": [ { "entidad": "ANIMAL", "id": "...", "en": "..." } ]
}
```

Lo puede usar cualquier miembro del establecimiento. Cada operación del push controla su propio
permiso (ej: un socio no puede registrar tratamientos: esa operación vuelve `RECHAZADO`).

## Pull: cambios por cursor `updated_at`

Todas las tablas tienen `updated_at`, que Hibernate actualiza en cada cambio. El pull recorre cada
tabla (lotes, rodeos, productos, animales, eventos y bajas) ordenada por `(updated_at, id)` y
devuelve las filas posteriores a la última que recibió el celular.

- **El cursor es opaco**: el celular solo lo guarda y lo devuelve. Adentro va, por tabla, el último
  `(updated_at, id)` entregado. El `id` desempata filas con el mismo `updated_at` (pasa cuando se
  modifican muchas juntas, ej: asignar 300 animales a un lote).
- **Paginado:** cada respuesta trae hasta `limite` filas en total. Con `hayMas: true` hay que llamar
  de nuevo con el cursor nuevo hasta que venga `false`. Las páginas de una misma vuelta usan el mismo
  tope de tiempo (`corte`), así los cambios nuevos no se mezclan a mitad de camino.
- **Margen:** no se entregan filas modificadas en los últimos 10 segundos (`SYNC_MARGEN`). Una
  transacción larga que todavía no terminó podría tener un `updated_at` anterior a lo ya entregado
  y quedar atrás del cursor; el margen lo evita. Lo recién cargado aparece en la sincronización
  siguiente (mientras tanto, el celular muestra su copia local).
- **Qué se manda:** todos los animales del establecimiento (también vendidos y muertos, con su
  `estado`), lotes y rodeos (los dados de baja con `activo: false`), productos sanitarios, y los
  eventos (los anulados con `anulado: true`).
- **Bajas:** cuando un animal se borra o se traslada a otro establecimiento, su fila ya no aparece
  en este. Para eso está la tabla `sync_baja`, que se llena sola (`BajaSyncListener`). El celular
  borra los animales de `bajas` (y sus eventos). Si el animal vuelve (se anula el traslado), la baja
  ya no se manda y el animal llega en `cambios`.
- **Cursor inválido** (de otro establecimiento, o de una versión vieja): responde 422 y no aplica
  nada. El celular tiene que borrar su copia local y sincronizar sin cursor.

Cómo aplica el celular una respuesta: guarda cada fila de `cambios` por `id` (reemplaza la que
tenga), borra las de `bajas` y recién ahí guarda el `cursor`. Si se corta a mitad, repite la llamada
con el cursor anterior: recibir dos veces la misma fila no hace daño.

## Push: operaciones

Cada operación tiene un `id` (UUID) que genera el celular. Se aplican en orden, cada una en su
propia transacción: si una falla, las demás se aplican igual.

| Tipo | Campos | Igual a |
| --- | --- | --- |
| `REGISTRAR_EVENTO` | `evento` (como `POST /eventos`) | Registrar un evento |
| `REGISTRAR_EVENTOS_POR_ANIMAL` | `eventoTipo`, `fecha`, `items: [{animalId, datos}]` | Grilla de pesajes |
| `ANULAR_EVENTO` | `eventoId` | Anular un evento |
| `ACTUALIZAR_ANIMAL` | `animalId`, `cambios: {campo: valor}`, `modificadoEn` | Editar la ficha |

**Todas son idempotentes**: si se pierde la respuesta y el celular reenvía, no se repiten. Los
eventos usan el `id` de la operación como `operacionId` (el backend ya ignoraba un `operacionId`
repetido); anular un evento ya anulado no hace nada; y una edición repetida no vuelve a aplicarse
(ver conflictos).

Resultado de cada operación:

- **`APLICADO`**: listo. Se saca de pendientes.
- **`RECHAZADO`**: no se puede aplicar y reintentar no sirve (datos inválidos, el animal ya se vendió,
  la caravana está repetida, sin permiso). Viene con `mensaje` y, si corresponde, `errores` por campo.
  El celular la muestra para corregirla o descartarla.
- **`ERROR`**: falla inesperada del servidor. El celular la reintenta más tarde.

## Conflictos

### Eventos: no hay conflictos

Los eventos no se editan: se registran o se anulan. Dos personas que cargan eventos sin conexión
sobre el mismo animal generan dos eventos, los dos válidos, como pasaría en papel.

Lo que sí puede pasar es que un evento ya no tenga sentido cuando llega: el animal se vendió
mientras tanto, o el lote se dio de baja. El servidor aplica las mismas validaciones que en la web y
lo devuelve `RECHAZADO` con el motivo. No se descarta en silencio: el usuario decide.

### Ficha del animal: last-write-wins por campo

Una edición de la ficha (`ACTUALIZAR_ANIMAL`) manda solo los campos que se cambiaron y cuándo se
cambiaron en el celular (`modificadoEn`). El servidor guarda, para cada campo, cuándo se modificó
por última vez (columna `animal.versiones`, también al editar desde la web).

**Regla:** cada campo se aplica solo si el `modificadoEn` del celular es **posterior** a la última
modificación de ese campo en el servidor. Si no, se descarta ese campo y gana lo del servidor. En
empate gana el servidor.

Ejemplo:

| Hora | Quién | Qué |
| --- | --- | --- |
| 9:00 | Celular de Juan, sin señal | Cambia la raza a "Angus" |
| 10:00 | Ana, en la web | Cambia la raza a "Hereford" y las observaciones |
| 11:00 | Juan recupera la señal | Se sincroniza su cambio de las 9:00 |

Resultado: la raza queda "Hereford" (el cambio de Ana es posterior) y la respuesta lo indica en
`descartados: ["raza"]`. Si Juan además hubiera cambiado la caravana, eso sí se aplica: Ana no la
tocó. Es "por campo": dos personas que editan cosas distintas del mismo animal no se pisan.

Detalles:

- **Campos que se resuelven así:** caravana, caravana electrónica, raza, fecha de nacimiento (y si es
  estimada) y observaciones. Lote, rodeo, categoría y estado no se editan con `ACTUALIZAR_ANIMAL`:
  cambian con eventos (movimiento, venta, baja), que no tienen conflictos.
- **Reloj del celular adelantado:** un `modificadoEn` en el futuro se toma como "ahora", así un
  celular con la hora mal no gana siempre. Un reloj atrasado sí puede hacer perder un cambio: es la
  limitación conocida de last-write-wins con relojes de cliente.
- **Validaciones:** si un valor es inválido (ej: caravana repetida en el establecimiento), la
  operación entera vuelve `RECHAZADO` y no se aplica ningún campo.
- **Animales anteriores a esto:** un campo sin versión vale la fecha de alta del animal.

## Pendiente (próximos PRs del issue)

- La PWA: service worker, IndexedDB, cola de operaciones y carga de eventos sin conexión.
- El indicador de pendientes de sincronizar.
- Hoy solo se sincroniza el establecimiento activo. Si un usuario pierde el acceso al
  establecimiento, `/api/sync` responde 404 y el celular tiene que borrar esos datos.
