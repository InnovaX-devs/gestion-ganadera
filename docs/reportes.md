# Reportes (issue #32)

Sección **Reportes** de la web: se elige un reporte, se revisa en pantalla y se descarga en Excel o
PDF. Se pueden sacar de cualquier establecimiento del usuario, sin cambiar el activo.

| Reporte | Datos | Filtros |
| --- | --- | --- |
| Stock por categoría y lote | `GET /api/stock` | Establecimiento (es al día de hoy) |
| Existencias para vacunación / SENASA | `GET /api/establecimientos/{id}/reportes/existencias` | Establecimiento (al día de hoy) |
| Evolución de pesos y ganancia diaria | `GET /api/establecimientos/{id}/reportes/pesos` | Establecimiento, desde, hasta, lote, categoría |
| Índices reproductivos | — | Pendiente (ver abajo) |

## Cada reporte

- **Stock:** cabezas y % por categoría, y cuadros por lote y por rodeo con una columna por categoría.
- **Existencias:** datos del establecimiento con el **RENSPA**, cabezas por categoría y la planilla
  de animales activos ordenada por lote y caravana, con una columna "Vacunado" vacía para marcar a
  mano en la manga.
- **Pesos:** resumen, promedios por categoría, evolución del peso promedio por fecha (con gráfico en
  pantalla) y el detalle por animal. La **ganancia diaria** de cada animal es
  (último peso − primer peso) / días entre esas pesadas, contando solo los pesajes del período. Un
  animal con una sola pesada no tiene ganancia. Lote y categoría son los actuales del animal.
  Período por defecto: últimos 90 días; máximo: 3 años.

## Exportación

- Se hace en el navegador, a partir de lo mismo que se ve en pantalla (`web/src/features/reportes`):
  `modelo.ts` describe el reporte (título, encabezado, tablas) y `exportar.ts` lo convierte.
- **Excel** con `write-excel-file` (ya se usaba para la plantilla de importación): una hoja por
  tabla, con fechas y números de verdad (se pueden filtrar y sumar).
- **PDF** con `jspdf` y `jspdf-autotable`: A4, apaisado si alguna tabla tiene muchas columnas, con
  número de página. Las librerías se cargan recién al exportar.

## Pendiente

- **Índices reproductivos (% preñez, % destete):** necesitan los eventos de servicio, tacto y
  destete, que todavía no existen. El reporte aparece como "Próximamente".
- El stock y las existencias son siempre al día de hoy: reconstruir el stock de una fecha pasada
  necesita el historial de lotes de cada animal.
