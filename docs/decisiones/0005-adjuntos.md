# 0005 — Almacenamiento de fotos y archivos adjuntos

Estado: aceptada (issue #43)

## Contexto

Las observaciones (y a futuro otros eventos) y las fichas de animales necesitan fotos y PDF. Hay que
decidir dónde se guardan los bytes: base de datos, disco, o un almacén de objetos (S3, R2).

## Decisión

- Los archivos se guardan **en disco**, detrás de la interfaz `AlmacenArchivos`
  (`guardar` / `abrir` / `borrar`). En la base solo queda la metadata (tabla `adjunto`).
- La clave de cada archivo no depende del nombre original y nunca se acepta una ruta del cliente.
- El directorio se configura con `ADJUNTOS_DIR` (obligatorio en prod) y **debe estar en un volumen
  persistente**: sin volumen, cada deploy borra los archivos.
- Las descargas pasan siempre por la API (multi-tenant + permisos); no hay URLs públicas.

## Por qué disco y no S3/R2 ya

- Cero dependencias y cero costo para arrancar; los volúmenes de la mayoría de los hosts alcanzan.
- Con la interfaz, pasar a R2/S3 es sumar una implementación y cambiar la configuración, sin tocar
  controladores, modelo ni app.

## Cuándo migrar

Recomendado **Cloudflare R2** (compatible con S3, sin costo de salida) cuando haya más de una
instancia del backend, el volumen se acerque a decenas de GB o se necesiten backups independientes.
Hay que copiar los archivos existentes usando la misma clave.

## Consecuencias

- Los backups de la base **no** incluyen los archivos: hay que respaldar el volumen aparte.
- Límites por defecto: 10 MB por archivo, 10 archivos por evento o animal. Solo JPG, PNG, WebP, HEIC y PDF,
  validados por contenido (firma) y no por la extensión.
- La app comprime las fotos (lado mayor 1600 px) antes de subirlas, para cuidar los datos móviles.
