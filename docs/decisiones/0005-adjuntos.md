# 0005 — Almacenamiento de fotos y archivos adjuntos

Estado: aceptada (issue #43)

## Contexto

Las observaciones (y a futuro otros eventos) y las fichas de animales necesitan fotos y PDF. Hay que
decidir dónde se guardan los bytes: base de datos, disco, o un almacén de objetos (S3, R2).

## Decisión

- La interfaz `AlmacenArchivos` (`guardar` / `abrir` / `borrar`) esconde dónde viven los bytes. En la base
  queda solo la metadata (tabla `adjunto`).
- Hay dos implementaciones, elegidas con `ADJUNTOS_ALMACEN`:
  - `disco` (`AlmacenEnDisco`): default en desarrollo y tests. Usa `ADJUNTOS_DIR`.
  - `r2` (`AlmacenEnR2`): **producción**. Cloudflare R2 (API compatible con S3).
- En producción `ADJUNTOS_ALMACEN` es obligatoria: no hay valor por defecto para no terminar por error en un
  disco que se borra.
- La clave de cada archivo no depende del nombre original y nunca se acepta una ruta del cliente.
- El bucket es **privado**. Las descargas pasan siempre por la API (multi-tenant + permisos); no hay URLs públicas.

## Por qué R2 en producción

- El backend corre en Render, donde el disco se borra en cada despliegue. Un disco persistente es de pago,
  limita a una instancia y los backups son aparte.
- R2 incluye 10 GB, 1 M de operaciones de escritura y 10 M de lectura gratis por mes; descargar no cuesta.
  Pasado el plan gratuito: USD 0,015 por GB al mes.
- No depende de Render ni de Vercel, así que cambiar de host no obliga a mover las fotos.
- Vercel Blob se descartó: 1 GB gratis (si se supera, se bloquea 30 días) y sin cliente Java ni API S3.
  Supabase Storage hubiera sido razonable si la base estuviera ahí; la misma clase sirve apuntando su endpoint S3.

## Consecuencias

- Cambiar de proveedor S3-compatible es cambiar variables de entorno, no código.
- Los backups de la base **no** incluyen los archivos. R2 los guarda con redundancia, pero no versiona: borrar un
  archivo es definitivo.
- Limitar el gasto: la cuenta de Cloudflare tiene una tarjeta cargada que se cobra solo si se superan los límites
  gratuitos. Conviene activar alertas de uso en Cloudflare.
- Límites por defecto: 10 MB por archivo, 10 archivos por evento o animal. Solo JPG, PNG, WebP, HEIC y PDF,
  validados por contenido (firma) y no por la extensión.
- La app comprime las fotos (lado mayor 1600 px) antes de subirlas, para cuidar los datos móviles.
- El SDK de S3 está fijado en 2.29.x: desde la 2.30 agrega checksums por defecto que R2 no acepta. Al subirlo hay que
  configurar `requestChecksumCalculation(WHEN_REQUIRED)`.
