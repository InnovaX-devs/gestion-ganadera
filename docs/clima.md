# Clima del establecimiento (issue #29)

`GET /api/establecimientos/{id}/clima`: temperatura actual, pronóstico de 5 días y lluvia esperada.

## Fuente: Open-Meteo

- API gratuita y **sin clave**: [open-meteo.com](https://open-meteo.com). Pronóstico (`/v1/forecast`) y búsqueda de localidades (`geocoding-api.open-meteo.com/v1/search`).
- **Condiciones de la API gratuita** ([términos](https://open-meteo.com/en/terms)):
  - Solo para **uso no comercial**.
  - Menos de 10.000 llamadas por día, 5.000 por hora y 600 por minuto.
  - Los datos son CC BY 4.0: hay que mostrar **"Weather data by Open-Meteo.com"** con un link donde se usen. La web lo muestra al pie del widget.
- **Antes de cobrar por la app** hay que pasar al plan comercial: usa otro dominio (`customer-api.open-meteo.com`) y una clave. Se configura con variables de entorno, sin tocar código:
  ```
  CLIMA_URL=https://customer-api.open-meteo.com/v1
  OPEN_METEO_API_KEY=...
  ```
  La clave queda en el backend: la web nunca habla con Open-Meteo directamente.

## Cómo funciona

- **Ubicación:** si el establecimiento tiene latitud y longitud, se usan esas. Si no, se busca su localidad en Argentina (prefiriendo la de su provincia). Sin ninguna de las dos, responde 422 y la web invita a cargarla.
- **Caché en memoria:** un pronóstico se reutiliza 30 minutos (`CLIMA_CACHE`) para la misma zona (coordenadas redondeadas a ~1 km), y una localidad buscada, un día. Así un campo con muchos usuarios hace como mucho ~48 consultas por día, muy lejos del límite.
- **Si Open-Meteo no responde:** se devuelve el último pronóstico guardado si hay uno; si no, 503 con un mensaje claro.
- La caché es por instancia del backend y se pierde al reiniciar: alcanza para este volumen. Si algún día se corren varias instancias, conviene moverla a una caché compartida.

## Configuración

| Variable | Por defecto | Para qué |
| --- | --- | --- |
| `CLIMA_URL` | `https://api.open-meteo.com/v1` | API de pronóstico |
| `CLIMA_GEOCODING_URL` | `https://geocoding-api.open-meteo.com/v1` | Búsqueda de localidades |
| `OPEN_METEO_API_KEY` | (vacía) | Solo plan comercial |
| `CLIMA_DIAS` | `5` | Días de pronóstico (1 a 16) |
| `CLIMA_CACHE` | `30m` | Cuánto se reutiliza un pronóstico |
| `CLIMA_TIMEOUT` | `8s` | Tiempo máximo de espera |
