# Planes y límites (#35)

| Plan | Animales activos | Establecimientos | Usuarios |
|------|-----------------:|-----------------:|---------:|
| Inicial | 200 | 1 | 2 |
| Productor | 1000 | 2 | 4 |
| Empresa | sin límite | sin límite | sin límite |

Los límites están en la tabla `plan` (migración V17): para cambiarlos alcanza con un `UPDATE`, sin tocar código. El precio (`precio_mensual`) queda vacío hasta definirlo.

## Prueba gratis

- Una organización nueva tiene **30 días** de prueba con los límites de **Productor**. Se configuran con `PLAN_DIAS_PRUEBA` y `PLAN_PRUEBA`.
- Al terminar rige el plan contratado (`organizacion.plan_codigo`, Inicial por defecto), pero solo si la cuenta está paga: ver "Ningún plan es gratis".
- Las organizaciones que ya existían antes de V17 quedan en Inicial y sin prueba. Como ningún plan es gratis, sin prueba quedan en solo lectura hasta que se suscriban o el panel les cargue un pago manual. Para lanzar sin cortarlas: `COBRO_EXIGIR_PAGO=false` hasta acomodarlas.
- El plan y la prueba de una cuenta se cambian desde el **panel de administración** (abajo), sin tocar la base.

## Panel de administración

Para el equipo de Innovax: **`/admin`** en la web (en desarrollo, http://localhost:5173/admin). Es un panel aparte, con su propio login: no se entra con ninguna cuenta de cliente, y entrar al panel no te loguea en ninguna cuenta. Lista todas las cuentas con su dueño, plan, prueba y uso; desde ahí se crean cuentas, se cambia el plan o la prueba y se suspenden.

- **Quién entra:** un email de `ADMIN_EMAILS` (separados por coma) con la contraseña `ADMIN_PASSWORD`. Las dos van en el `.env` en desarrollo y en las variables del servidor en producción; no se guardan en la base. Sin `ADMIN_PASSWORD` el panel queda cerrado.
- **Sesión:** dura `ADMIN_SESION` (8 horas por defecto) y después pide la contraseña de nuevo. Botón **Salir** arriba a la derecha.
- **Intentos:** después de 5 contraseñas mal seguidas, el login del panel se cierra 15 minutos (para todos).
- **Seguridad:** el token del panel tiene rol ADMIN y solo sirve para `/api/admin/**`; el de un usuario tiene rol USUARIO y no entra al panel, aunque su email esté en `ADMIN_EMAILS`.
- API: `POST /api/admin/auth/login` (`{ email, password }` → `{ accessToken, email, expiresIn }`), `GET /api/admin/auth/me`, `GET /api/admin/organizaciones` y `PUT /api/admin/organizaciones/{id}/plan` (`{ planCodigo, pruebaHasta }`).
- Cada acción (ingresos, intentos fallidos, cambios) queda en el log del backend con el email de quien la hizo.

### Crear una cuenta

**Nueva cuenta** pide el nombre del campo, nombre y email del dueño, el plan y la prueba gratis (por defecto 30 días). Se crea la organización y el usuario dueño, y al dueño le llega un email con un enlace (`/activar/{token}`) para elegir su contraseña. Hasta que lo usa, la cuenta aparece como "Falta que el dueño active la cuenta".

- El enlace vence a los 7 días (igual que las invitaciones, `INVITACION_TTL`) y sirve una sola vez. Desde el menú **⋯** de la cuenta se puede **reenviar**; el enlace anterior deja de valer.
- En desarrollo el mail está apagado (`MAIL_ENABLED=false`): el enlace aparece en el log del backend.
- API: `POST /api/admin/organizaciones` (`{ nombreOrganizacion, nombre, apellido, email, planCodigo, pruebaHasta }`), `POST /api/admin/organizaciones/{id}/reenviar-activacion`; públicos `GET`/`POST /api/activacion/{token}` (`{ password }`).

### Suspender una cuenta

Desde el menú **⋯** → **Suspender cuenta**. Nadie de esa cuenta puede entrar: el login y el refresco de sesión responden 403 con `codigo: CUENTA_SUSPENDIDA`, y los tokens que ya tenían dejan de valer en menos de 30 segundos (el estado se cachea ese tiempo). Los datos no se tocan. **Reactivar cuenta** la habilita de nuevo.

- API: `PUT /api/admin/organizaciones/{id}/suspension` (`{ suspendida: true|false }`). Columna `organizacion.suspendida` (migración V18).

## Qué se bloquea ("bloqueo suave")

Se frena solo lo que **agrega**; lo cargado se sigue viendo y editando:

- Alta de animales a mano e **importación** desde Excel (también la vista previa).
- Crear establecimientos.
- Invitar usuarios. Las invitaciones pendientes ocupan lugar; invitar a alguien que ya es usuario de la cuenta a otro establecimiento no.

**No** se bloquea lo que viene del campo: nacimientos, cargas sin conexión que se sincronizan y revertir una venta o baja. Con eso se puede pasar del límite; la web lo avisa.

El error es un 422 con `codigo: "LIMITE_PLAN"`. En la web, "Mi plan" (menú del usuario) muestra el uso, y una franja avisa cuando se llega a un límite o faltan 7 días o menos para que termine la prueba.

## Cobro con Mercado Pago (suscripciones)

El cliente contrata y paga solo desde **Mi plan**: elige un plan, paga en Mercado Pago y se le cobra todos los meses. El plan se activa y se renueva solo.

### Precios

Se cargan en el panel de Innovax → **Precios** (pesos por mes). Un plan sin precio (o en 0) no se contrata online. Cambiar un precio no toca las suscripciones ya hechas: siguen con el monto con que se suscribieron; el nuevo rige para las nuevas.

### Cómo funciona

1. **Suscribirse:** solo el dueño de la cuenta (quien la creó). Se crea la suscripción en Mercado Pago (`POST /preapproval`, sin plan asociado, con pago pendiente) y el cliente va al link de Mercado Pago a pagar.
2. **Volver:** Mercado Pago lo devuelve a `/plan?preapproval_id=...` y la web consulta enseguida el estado (`POST /api/suscripcion/sincronizar`), sin esperar el aviso.
3. **Avisos (webhook):** Mercado Pago avisa cada cambio de la suscripción y cada cobro a `POST /api/mercadopago/webhook`. El aviso nunca se toma como verdad: solo dispara una consulta a Mercado Pago con nuestro Access Token. Si hay `MP_WEBHOOK_SECRET`, además se valida la firma (`x-signature`).
4. **Autorizada:** el plan contratado de la cuenta pasa a ser el de la suscripción (la prueba gratis no se toca). Si tenía otra suscripción, se cancela en Mercado Pago.
   **Primer cobro:** si se suscribe durante la prueba gratis (o con un pago manual vigente), carga la tarjeta ahora pero el primer cobro es el día después de que termina (`auto_recurring.start_date`). Si no, se cobra enseguida.
5. **Cada cobro aprobado** paga un mes: el plan rige hasta un mes después del último cobro aprobado.
6. **Cambiar de plan:** desde Mi plan, "Cambiar a este plan" crea una suscripción nueva; cuando se autoriza, la anterior se cancela.
7. **Cancelar:** el dueño desde Mi plan. No se cobra más y la cuenta se usa hasta el último día pago; después queda en solo lectura.

### Ningún plan es gratis: cuenta sin pago

Una cuenta puede usarse si tiene **alguna** de estas:

- la **prueba gratis** vigente,
- un **pago manual** vigente (lo carga el panel: ver abajo),
- una **suscripción al día**: activa o pausada, hasta lo pago + los días de gracia; cancelada, hasta el último día pago.

Si no tiene ninguna, la cuenta queda en **solo lectura**:

- Puede entrar y **ver todo** (animales, eventos, reportes, exportar).
- Lo que **carga, modifica o borra** responde `402` con `codigo: "PAGO_REQUERIDO"` ("Tu cuenta está en solo lectura…"). Quedan libres el login, Mi plan, la suscripción y la **sincronización** (`/api/sync`): lo cargado en el campo sin señal se guarda igual.
- La web muestra una franja roja fija en todas las pantallas con el botón para elegir un plan.

No se borra nada: al pagar, vuelve a funcionar enseguida.

Se controla en cada request (`PagoRequeridoInterceptor`, con el estado en caché `COBRO_CACHE_ESTADO`, 30 s). Con `COBRO_EXIGIR_PAGO=false` no se pausa a nadie (por ejemplo, para lanzar el cobro sin cortar cuentas que ya existían).

### Si no se puede cobrar (impago)

- Mercado Pago reintenta el cobro durante unos días.
- Mientras tanto la cuenta sigue funcionando **`COBRO_DIAS_GRACIA` días** (7 por defecto) después del último día pago, con una franja roja: "No pudimos cobrar tu plan… si no se cobra, desde el DD la cuenta queda en solo lectura".
- Si se termina la gracia sin cobro, la cuenta queda en solo lectura. Un job diario (`app.jobs.suscripciones-cron`, a las 4) además marca la suscripción **VENCIDA**.
- Si después el cobro sale (Mercado Pago reintentó o el cliente actualizó su tarjeta), vuelve a funcionar sola.

### Pago manual (transferencia, efectivo)

Quien paga por transferencia paga **del 1 al 10 de cada mes** (`COBRO_DIA_VENCIMIENTO`, 10 por defecto), por ese mes.

- **Todos los meses:** en el panel, menú **⋯** de la cuenta → **Registrar pago**. El menú ya dice hasta cuándo va a quedar pagada:
  - si no tenía pago o se le venció: hasta el **10 del mes que viene** (paga el 5/11 o el 25/11 → hasta el 10/12);
  - si todavía estaba al día (paga por adelantado): **un mes más** desde lo que tenía (hasta el 10/12 → hasta el 10/1).
- El día después del vencimiento (el 11) queda en solo lectura hasta que se registre el pago.
- **A mano:** en **Cambiar plan** → **Pago manual** → "Pagado hasta" (por defecto, el 10 del mes que viene; atajos +1, +3 y +12 meses). Sirve para pagos por varios meses o fechas especiales.

Hasta ese día la cuenta se usa sin suscripción. Se guarda en `organizacion.pago_manual_hasta` (migración V20). API: `POST /api/admin/organizaciones/{id}/registrar-pago`.

En el panel, cada cuenta muestra su estado de cobro: pagado hasta, pago manual, impaga, cancelada, pago pendiente o "Sin pago: solo lectura".

### Configuración (`.env`)

| Variable | Qué es |
|---|---|
| `MP_ACCESS_TOKEN` | Access Token de la aplicación (Tus integraciones → la app → Credenciales). Vacío = el pago online está apagado y Mi plan dice "hablá con nosotros". |
| `MP_WEBHOOK_SECRET` | Clave secreta de los webhooks (Tus integraciones → la app → Webhooks). En producción, obligatoria. |
| `MP_BACK_URL` | A dónde vuelve el cliente después de pagar. Por defecto `APP_WEB_URL/plan`. |
| `MP_EMAIL_PAGADOR_PRUEBA` | **Solo para probar.** Mercado Pago exige que el pagador sea un usuario de prueba; si está, se usa este email en vez del del usuario. En producción, vacío. |
| `COBRO_DIAS_GRACIA` | Días de gracia si no se puede cobrar (7). |
| `COBRO_DIA_VENCIMIENTO` | Pago por transferencia: se paga del 1 a este día de cada mes (10). |
| `COBRO_EXIGIR_PAGO` | `true` (por defecto): sin prueba, suscripción ni pago manual, la cuenta queda en solo lectura. `false`: no se bloquea a nadie. |

### Probarlo en desarrollo

Mercado Pago no deja que una cuenta real le pague a una de prueba (ni al revés), así que se usan **cuentas de prueba**:

1. En Mercado Pago Developers → tu aplicación → **Cuentas de prueba**, creá dos: un **vendedor** y un **comprador** (país Argentina).
2. Entrá a Mercado Pago Developers **con el vendedor de prueba** (usuario y contraseña que muestra la pantalla), creá una aplicación de Suscripciones y copiá sus **credenciales de producción** a `MP_ACCESS_TOKEN`. Sí, de producción: las del vendedor de prueba no cobran plata real.
3. En `MP_EMAIL_PAGADOR_PRUEBA` poné el email del **comprador** de prueba (termina en `@testuser.com`).
4. Si Mercado Pago rechaza la URL de retorno por ser `localhost`, poné `MP_BACK_URL=https://www.innovax-team.com` y, después de pagar, volvé a Mi plan y tocá **"Ya pagué, actualizar"**.
5. En el panel de Innovax cargá precios, entrá a la app como dueño de una cuenta, Mi plan → **Suscribirme**, y en Mercado Pago **iniciá sesión con el comprador de prueba** y pagá con una **tarjeta de prueba** (Tus integraciones → Tarjetas de prueba; titular `APRO` para aprobado, `OTHE` para rechazado).

Los avisos (webhooks) solo llegan si el backend es accesible desde internet. En desarrollo no hace falta: "Ya pagué, actualizar" y la vuelta desde Mercado Pago consultan el estado directamente. Para probar los avisos se puede exponer el backend con un túnel (ngrok, Cloudflare Tunnel) y configurar esa URL en Webhooks.

### Producción

1. Activá las credenciales de producción de la aplicación real (la de la cuenta de Innovax) y ponelas en `MP_ACCESS_TOKEN`.
2. En Webhooks configurá `https://<backend>/api/mercadopago/webhook`, eventos de **Suscripciones** (planes y suscripciones, y pagos recurrentes), y copiá la clave secreta a `MP_WEBHOOK_SECRET`.
3. `MP_BACK_URL` = `https://<web>/plan` y `MP_EMAIL_PAGADOR_PRUEBA` vacío.

### API

- `POST /api/suscripcion` `{ planCodigo }` → `{ url }` (link de Mercado Pago). Solo el dueño.
- `DELETE /api/suscripcion`: cancela. Solo el dueño.
- `POST /api/suscripcion/sincronizar` → el plan actualizado.
- `GET /api/plan` trae `cobro`: `habilitado`, `diasGracia`, `suscripcion` (estado, pagadoHasta, enGracia, hasta) y `pendiente` (link para terminar el pago).
- `GET /api/admin/planes`, `PUT /api/admin/planes/{codigo}` `{ precioMensual }` (panel).
- Tabla `suscripcion` (migraciones V19 y V21, `primer_cobro`). Estados: PENDIENTE, ACTIVA, PAUSADA, CANCELADA, VENCIDA, REEMPLAZADA.
