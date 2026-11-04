# Deploy (producción)

Cómo está armado:

```
 Navegador / celular
        │
        ▼
 Vercel ─ web (React)            https://gestion-ganadera-innovax.vercel.app
        │  llama a la API
        ▼
 Render ─ backend (Spring Boot)  https://gestion-ganadera-zxg1.onrender.com
        │
        ▼
 Supabase ─ base PostgreSQL

 UptimeRobot: cada 5 min llama a /api/health para que Render no se duerma
 (y de paso Supabase no se pausa: /api/health consulta la base).
```

Todo en planes gratis. Límites a tener en cuenta:

- **Render gratis** se duerme a los 15 minutos sin tráfico (tarda ~1 minuto en despertar) y da 750 horas por mes: alcanza para **un** servicio prendido todo el mes. El robot de UptimeRobot lo mantiene despierto, así también corren las tareas programadas (alertas sanitarias, vencimientos de pago, cotizaciones).
- **Supabase gratis** pausa el proyecto tras una semana sin actividad (el robot lo evita) y tiene 500 MB de base.
- Cuando haya clientes que pagan, conviene pasar el backend a Render Starter (US$ 7/mes): no se duerme y no depende del robot.

Cada vez que se mergea a `main`, Render y Vercel vuelven a publicar solos.

---

## 0. Cuentas que hacen falta

Entrá con la cuenta de GitHub de Innovax (la que tiene el repo) en:

- [supabase.com](https://supabase.com) → base de datos
- [render.com](https://render.com) → backend
- [vercel.com](https://vercel.com) → web
- [uptimerobot.com](https://uptimerobot.com) → el robot
- [resend.com](https://resend.com) → envío de mails (API key)

## 1. Llevar el código a `main`

Render y Vercel publican la rama `main`. Hacé el PR **develop → main** y mergealo (con lo de `backend/Dockerfile` y `web/vercel.json`, que son los archivos de deploy).

## 2. Supabase (base de datos)

1. **New project**. Nombre: `gestion-ganadera`. **Region: East US (North Virginia)** (la misma zona que el backend en Render: así la base responde rápido).
2. **Database password**: generá una y **guardala** (la vas a necesitar en el paso 4).
3. Si al crear aparece la opción **Enable Data API**, **destildala**. El sistema no usa la API automática de Supabase, y dejarla prendida expondría las tablas. Si no apareció: *Project Settings → Data API* y desactivala.
4. Cuando termine de crear: botón **Connect** (arriba) → **Session pooler** (no "Direct connection": Render no la puede usar). Vas a ver algo así:

   ```
   postgresql://postgres.abcdefghij:[YOUR-PASSWORD]@aws-0-us-east-1.pooler.supabase.com:5432/postgres
   ```

   De ahí salen tres variables (con tus valores):

   | Variable | Valor |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` |
   | `DB_USERNAME` | `postgres.abcdefghij` (lo que está antes de los `:`) |
   | `DB_PASSWORD` | la contraseña del punto 2 |

Las tablas las crea el backend solo al arrancar (migraciones Flyway).

## 3. Claves

En la terminal (WSL):

```bash
openssl rand -base64 48   # → JWT_SECRET
openssl rand -base64 24   # → ADMIN_PASSWORD (contraseña del panel /admin)
```

Guardalas en un lugar seguro (gestor de contraseñas). No van al repo.

## 4. Render (backend)

1. **New → Web Service** → conectá GitHub → elegí el repo `gestion-ganadera`.
2. Completá:

   | Campo | Valor |
   |---|---|
   | Name | `gestion-ganadera-api` (de acá sale la dirección) |
   | Region | **Virginia (US East)** |
   | Branch | `main` |
   | Root Directory | `backend` |
   | Language / Runtime | **Docker** |
   | Instance Type | **Free** |

3. **Advanced → Health Check Path**: `/api/health`.
4. **Environment Variables** (*Add from .env* deja pegar todas juntas):

   ```
   SPRING_PROFILES_ACTIVE=prod
   DB_URL=jdbc:postgresql://...pooler.supabase.com:5432/postgres?sslmode=require
   DB_USERNAME=postgres.xxxxx
   DB_PASSWORD=...
   DB_POOL_SIZE=5
   JWT_SECRET=...
   APP_WEB_URL=https://pendiente.vercel.app
   CORS_ALLOWED_ORIGINS=https://pendiente.vercel.app
   MAIL_HOST=smtp.resend.com
   MAIL_PORT=587
   MAIL_USERNAME=resend
   MAIL_PASSWORD=re_...
   MAIL_FROM=onboarding@resend.dev
   ADMIN_EMAILS=innovax.devs@gmail.com
   ADMIN_PASSWORD=...
   MP_ACCESS_TOKEN=APP_USR-...
   MP_EMAIL_PAGADOR_PRUEBA=...@testuser.com
   ```

   `APP_WEB_URL` y `CORS_ALLOWED_ORIGINS` se completan en el paso 6, cuando exista la web. `MP_*` por ahora son las de **prueba** (ver "Mercado Pago" abajo).

5. **Create Web Service**. La primera vez tarda 5 a 10 minutos (baja Maven y compila). En **Logs** tiene que aparecer `Started GanaderaApplication`.
6. Probalo: abrí `https://gestion-ganadera-zxg1.onrender.com/api/health` → tiene que responder con el estado de la base en OK.

## 5. Vercel (web)

1. **Add New → Project** → importá el repo `gestion-ganadera`.
2. **Root Directory**: `web` (botón *Edit*). Framework: **Vite** (lo detecta solo).
3. **Environment Variables**:

   ```
   VITE_API_URL=https://gestion-ganadera-zxg1.onrender.com
   ```

   Sin `/api` al final y sin barra final.
4. **Deploy**. Al terminar te da la dirección, por ejemplo `https://gestion-ganadera.vercel.app`.

## 6. Unir web y backend

En Render → el servicio → **Environment**, cambiá:

```
APP_WEB_URL=https://gestion-ganadera.vercel.app
CORS_ALLOWED_ORIGINS=https://gestion-ganadera.vercel.app
```

Exactamente la dirección de Vercel, sin barra al final. **Save, rebuild and deploy**.

## 7. UptimeRobot (que no se duerma)

**New monitor** → tipo **HTTP(s)** → URL `https://gestion-ganadera-zxg1.onrender.com/api/health` → intervalo **5 minutos** → Create. Si el backend se cae, además te avisa por mail.

## 8. Probar

1. Abrí la web → **Creá una cuenta** → entrás con 30 días de prueba.
2. `https://<web>/admin` → entrás con un email de `ADMIN_EMAILS` y `ADMIN_PASSWORD`.
3. Panel → **Precios** → cargá los precios.
4. Instalá la app en el celular (botón "Instalar") y probá cargar algo sin señal.

## Mails (Resend)

El plan gratis de Render **bloquea los puertos SMTP** (25, 465 y 587). Por eso en producción los mails salen por la **API HTTP de Resend** (puerto 443), con la misma clave `MAIL_PASSWORD`: es el valor por defecto (`MAIL_PROVEEDOR=resend`), no hay que configurar nada más. `MAIL_HOST`, `MAIL_PORT` y `MAIL_USERNAME` solo se usan con `MAIL_PROVEEDOR=smtp` (por ejemplo en un plan pago de Render u otro hosting).

Si un mail falla, el motivo queda en los **Logs** de Render (`No se pudo enviar...`), y desde el panel se puede volver a mandar con **Reenviar enlace**.

Con `MAIL_FROM=onboarding@resend.dev`, Resend **solo manda al email del dueño de la cuenta de Resend**. Para mandar a clientes: en Resend → **Domains** → agregar `innovax-team.com` (o un subdominio), cargar los registros DNS que pide en el proveedor del dominio y, ya verificado, poner `MAIL_FROM=no-reply@innovax-team.com`.

## Mercado Pago

Mientras se prueba, quedan las credenciales de **prueba** (vendedor de prueba + `MP_EMAIL_PAGADOR_PRUEBA`). Para cobrar de verdad, en Render:

- `MP_ACCESS_TOKEN` = Access Token de **producción** de la cuenta real de Innovax.
- **Borrar** `MP_EMAIL_PAGADOR_PRUEBA`.
- En la aplicación de Mercado Pago → **Webhooks**: URL `https://gestion-ganadera-zxg1.onrender.com/api/mercadopago/webhook`, eventos de **Suscripciones**; copiar la clave secreta a `MP_WEBHOOK_SECRET`.

`MP_BACK_URL` no hace falta: en producción vuelve a `APP_WEB_URL/plan`. Detalle en [planes.md](planes.md).

## Problemas comunes

| Síntoma | Causa probable |
|---|---|
| Render: `Connection refused` / `Network is unreachable` a la base | Se usó la conexión **Direct** de Supabase. Usar la de **Session pooler**. |
| Render: `password authentication failed` | `DB_USERNAME` tiene que ser `postgres.xxxxx` (con el punto y el código del proyecto). |
| Render: `Could not resolve placeholder 'MAIL_HOST'` (u otra) | Falta esa variable en Environment. |
| Crear una cuenta tarda y dice "No se pudo conectar con el servidor" | El mail quedó esperando un puerto SMTP bloqueado. Usar `MAIL_PROVEEDOR=resend` (el valor por defecto). |
| "No se puede pagar con el email de la cuenta de Mercado Pago que cobra" | MP no deja que alguien se pague a sí mismo: la cuenta de la app tiene que tener otro email. |
| Log: `Resend rechazó el email (403)` | El dominio de `MAIL_FROM` no está verificado en Resend, o con `onboarding@resend.dev` se mandó a otro email que no es el dueño de la cuenta de Resend. |
| La web carga pero el login dice "No se pudo conectar con el servidor" | `VITE_API_URL` mal en Vercel (cambiarla y volver a desplegar), o `CORS_ALLOWED_ORIGINS` no coincide exacto con la dirección de la web. |
| Recargar `/plan` o `/admin` da 404 | Falta `web/vercel.json` en `main`. |
| La primera carga tarda un minuto | Render estaba dormido: revisar que UptimeRobot esté activo. |
| Render: el servicio se reinicia solo (memoria) | La instancia gratis tiene 512 MB. Si pasa seguido, Starter (US$ 7). |
