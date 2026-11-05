# Fotos y archivos adjuntos

Decisión de almacenamiento: [ADR 0005](decisiones/0005-adjuntos.md).

## Qué se puede adjuntar

- **A un evento** (hoy, desde Observación): fotos y PDF. Se cuelgan de la operación (`operacionId`), así que
  un evento sobre varios animales comparte los mismos archivos.
- **A un animal**: desde la tarjeta "Fotos y archivos" de la ficha.
- Formatos: JPG, PNG, WebP, HEIC, PDF. Máximo 10 MB y 10 archivos por elemento (configurable).

## API

Todo bajo `/api/establecimientos/{establecimientoId}`. Subida en `multipart/form-data`, parte `archivo`
y `cargaId` opcional (UUID generado por la app: reintentar con el mismo no duplica).

| Endpoint | Permiso |
|----------|---------|
| `POST /eventos/operaciones/{operacionId}/adjuntos` | El del tipo de evento |
| `POST /animales/{animalId}/adjuntos` | Gestionar animales |
| `GET /animales/{animalId}/adjuntos` | Ver |
| `GET /adjuntos/{id}` | Ver (descarga) |
| `DELETE /adjuntos/{id}` | El mismo que para subir |

Los eventos devuelven `adjuntos` (nunca null). Al anular un evento sus archivos siguen visibles en la
línea de tiempo; al dar de baja un animal se borran sus archivos y los de eventos anulados.

## App

- "Sacar foto" abre la cámara trasera; "Elegir archivos" abre la galería.
- Las fotos se achican y recomprimen antes de subir.
- **Sin conexión**: el archivo queda en IndexedDB y se sube solo al sincronizar, después de que el evento
  llegó al servidor. Aparece en el indicador de sincronización.
- Errores permanentes (4xx) descartan el archivo con aviso; los transitorios (red, 401, 408, 429, 5xx) se reintentan.

## Configuración

| Variable | Qué es |
|----------|--------|
| `ADJUNTOS_ALMACEN` | `disco` (default en desarrollo) o `r2`. **Obligatoria en producción.** |
| `ADJUNTOS_DIR` | Carpeta de los archivos, con `disco` (default dev `./data/adjuntos`) |
| `ADJUNTOS_BUCKET` | Nombre del bucket de R2 (con `r2`) |
| `ADJUNTOS_ENDPOINT` | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (con `r2`) |
| `ADJUNTOS_ACCESS_KEY` / `ADJUNTOS_SECRET_KEY` | Claves del token de API de R2, con permiso Object Read & Write limitado al bucket |
| `ADJUNTOS_TAMANIO_MAXIMO` | Tamaño máximo por archivo (default `10MB`) |
| `ADJUNTOS_MAXIMO_POR_ELEMENTO` | Archivos por evento o animal (default `10`) |

## Puesta en marcha de R2

1. Cuenta en Cloudflare y activar R2 (plan gratuito; puede pedir tarjeta).
2. Crear un bucket **privado**, clase Standard, sin acceso público ni dominio `r2.dev`.
3. En R2, *Manage API tokens*: crear un token Object Read & Write limitado a ese bucket. El secret se ve una sola vez.
4. Cargar las variables de arriba en el servicio del backend (Render). No van al repositorio.
5. No hace falta CORS: el navegador nunca habla con R2, todo pasa por la API.
