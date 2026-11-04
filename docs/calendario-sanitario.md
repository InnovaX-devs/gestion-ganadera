# Calendario sanitario y alertas (issue #34)

Sección **Calendario sanitario** (menú Hacienda): se planifican vacunas y tratamientos para una
fecha, se ven en un calendario mensual (en el celular, como agenda) y llegan avisos por email.

## Planificar

- Cada tratamiento planificado tiene fecha, a quién se aplica (**un lote, un rodeo o todo el
  establecimiento**), qué se hace, y opcionalmente producto, dosis y notas.
- **Repetir:** al crearlo se puede repetir de 2 a 12 veces cada N días (segunda dosis, refuerzos).
- Estados: **pendiente**, **hecho** o **cancelado** ("no se hace"). Un pendiente con fecha pasada
  aparece como **vencido**.
- **Registrar tratamiento:** abre el formulario de siempre ya completo (lote o rodeo, producto,
  dosis, vía, carencia). Al guardarlo, el plan queda hecho y guarda la operación del tratamiento.
  Sin conexión, el tratamiento se guarda en el dispositivo y el plan se marca a mano después.
- Lo ve cualquier miembro; lo arma quien puede registrar sanidad (dueño, encargado, veterinario).

```
GET    /api/establecimientos/{id}/plan-sanitario?desde=&hasta=   // por defecto, el mes en curso
POST   /api/establecimientos/{id}/plan-sanitario                 // devuelve todos los creados
PUT    /api/establecimientos/{id}/plan-sanitario/{planId}        // solo pendientes
POST   .../{planId}/realizado   { "operacionId": "..." }         // opcional
POST   .../{planId}/reabrir | /cancelar
DELETE .../{planId}
```

## Alertas

`GET /api/establecimientos/{id}/alertas`, en el calendario y en el inicio (solo si hay algo):

- **Tratamientos vencidos:** pendientes con fecha pasada.
- **Próximos:** pendientes de hoy a 7 días (`ALERTAS_DIAS_PROXIMOS`).
- **Vencimientos de carencia:** animales activos que terminan la carencia en los próximos 7 días
  (desde esa fecha se pueden vender o faenar), con el producto. Usa el mismo cálculo que la ficha
  del animal (`CarenciaService`).
- **Partos próximos: pendiente.** La fecha probable de parto sale del servicio o del tacto, que
  todavía no se registran. Cuando existan, se suman acá y en el email.

## Avisos por email

- Job diario `AlertasJob`, a las 7 (hora de Argentina, `JOB_ALERTAS_CRON`). Recorre las
  organizaciones de a una (como el job de categorías, ADR 0003).
- Solo hay email si **hoy pasa algo**: llega el día de avisar de un tratamiento ("avisar N días
  antes", configurable de 0 a 30), es el día del tratamiento, ayer quedó vencido, o mañana un animal
  termina la carencia. El email trae el panorama completo: vencidos, de hoy, próximos y carencias.
- Lo reciben los miembros que pueden registrar sanidad y no los desactivaron (en el calendario,
  "Avisos por email"; es por usuario, `PUT /api/usuario/preferencias`).
- Un email por día, por establecimiento y por usuario (tabla `alerta_enviada`): si el job corre dos
  veces, no se repite.
- En desarrollo (`MAIL_ENABLED=false`) los emails se escriben en el log.

**Push y WhatsApp:** más adelante. La idea es una interfaz de canal como `EmailSender`, y que el
usuario elija por cuál quiere recibir.
