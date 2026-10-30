# Cotizaciones de mercado (issue #27)

Investigación de fuentes para el job diario de cotizaciones, y cómo está armado.

## Resumen

| Datos | Fuente | Cómo se obtiene | Estado |
| --- | --- | --- | --- |
| Dólar oficial y blue | [DolarApi](https://dolarapi.com) | API JSON pública | **Automático** (`DolarApiFuente`) |
| Maíz, soja, trigo | [Cámara Arbitral de Cereales – BCR](https://www.cac.bcr.com.ar/es/precios-de-pizarra) | Lectura de la página | **Automático** (`PizarraRosarioFuente`) |
| Novillo, novillito | [Mercado Agroganadero de Cañuelas](https://www.mercadoagroganadero.com.ar/dll/hacienda1.dll/haciinfo000001) | La tabla se carga al consultar por fechas | **Pendiente** (ver abajo) |
| Ternero, ternera | [ROSGAN](https://www.rosgan.com.ar) | Resultados de cada remate | **Pendiente** (ver abajo) |

## Fuentes

### Dólar: DolarApi

- API pública, gratuita y sin clave: `GET https://dolarapi.com/v1/dolares/oficial` y `/v1/dolares/blue`.
- Código abierto con licencia MIT ([github.com/enzonotario/esjs-dolar-api](https://github.com/enzonotario/esjs-dolar-api)). Toma los valores de sitios de cotizaciones (DolarHoy, Ámbito).
- Se guarda el valor de **venta**.
- Riesgo: es un proyecto de la comunidad, sin garantía de disponibilidad. Alternativa para el oficial: la API del BCRA (solo mayorista/minorista oficial, no tiene blue).

### Granos: precios pizarra de Rosario (BCR)

- La Cámara Arbitral de Cereales publica a la mañana la pizarra del día hábil anterior, en $/tn (trigo, maíz, girasol, soja, sorgo). Cuando no hubo operaciones publica un estimativo (`S/C (E)`): se guarda igual, con una nota.
- **No hay API pública.** La BCR tiene una API (Protocolo GIX, endpoint "Precios Cámara") pero es para empresas registradas, con usuario.
- **Términos de uso:** el sitio dice "Todos los derechos reservados" y no tiene condiciones específicas para el uso de los precios. Son precios de referencia públicos (Decreto 1058/99), pero antes de producción conviene **consultar a la BCR** (contacto@bcr.com.ar) si se pueden mostrar en la app citando la fuente, o pedir acceso a GIX.
- Se lee la página una vez por día, pasándola a texto, para no depender del HTML exacto. Si cambia el formato, la fuente falla con un mensaje claro y se mantiene el último valor.

### Novillo y novillito: Mercado Agroganadero (Cañuelas)

- Publica precios por categoría de cada rueda (lunes a viernes): "Precios por Categoría" y "Resumen de Precios".
- **No hay API.** Las páginas de precios muestran la tabla vacía hasta que se consulta un rango de fechas: hay que averiguar cómo hace esa consulta (parámetros del formulario) para poder automatizarla.
- **Términos de uso:** no encontramos condiciones publicadas. Hay que consultar al mercado antes de usar los datos en producción.
- Alternativa oficial: el Ministerio de Agricultura publica datos abiertos de hacienda ([datos.magyp.gob.ar](https://datos.magyp.gob.ar/dataset/indicadores-economicos-para-ganaderia-bovina)), con licencia abierta, pero son **mensuales o anuales**, no diarios.

### Ternero y ternera: ROSGAN

- El índice de referencia del ternero sale de los **remates** de ROSGAN, que no son diarios (aproximadamente uno o dos por mes). Por eso, aunque el job corra todos los días, `fechaDato` va a ser la del último remate.
- **No hay API pública** ni condiciones de uso publicadas para reutilizar los precios.

## Pendiente para completar hacienda

Para sumar novillo/novillito y ternero/ternera hay que:

1. Averiguar cómo consulta los datos cada sitio (en el navegador, herramientas de desarrollador, pestaña Red, al buscar un rango de fechas).
2. Confirmar con el Mercado Agroganadero y con ROSGAN si se pueden usar los datos.
3. Agregar una clase que implemente `FuenteCotizacion` (ver abajo). No hace falta tocar nada más.

Mientras tanto esos productos aparecen en `/api/mercado/hoy` con estado `SIN_DATO`, y la relación de reposición no se calcula.

## Cómo funciona

- **Tabla `cotizacion`** (migración V13): una fila por día y producto. Sin `organizacion_id`: son datos compartidos.
- **Job** `ActualizacionCotizacionesJob`: todos los días a las 19 hs (hora de Argentina), `app.jobs.cotizaciones-cron`. Si la tabla está vacía, corre también al arrancar.
- **Si una fuente falla**, sus productos copian el último valor conocido con estado `ANTERIOR` (conservan su `fechaDato` original) y el error queda en la columna `error` y en el log. Si nunca hubo valor, quedan `SIN_DATO`. Las demás fuentes se guardan igual. Un reintento fallido el mismo día no pisa un valor que ya se había leído bien.
- **Relación de reposición** = precio del kilo de ternero / precio del kilo de novillo, con 2 decimales. Cuánto más caro es reponer que vender: con 1,35, un kilo de ternero cuesta un 35 % más que un kilo de novillo. (ROSGAN en sus informes usa el novillito como referencia del "gordo"; el issue pide novillo.)

### Agregar una fuente

```java
@Component
@ConditionalOnProperty(name = "app.mercado.fuentes-externas", havingValue = "true", matchIfMissing = true)
public class MiFuente implements FuenteCotizacion {
    public String nombre() { return "Mi fuente"; }
    public Set<Producto> productos() { return EnumSet.of(Producto.NOVILLO, Producto.NOVILLITO); }
    public List<Lectura> leer() {
        // Lectura.ok(producto, valor, fechaDelDato, nota) o Lectura.fallo(producto, motivo).
        // Si toda la fuente falla, tirar una excepción.
    }
}
```

### Configuración

| Variable | Por defecto | Para qué |
| --- | --- | --- |
| `JOB_COTIZACIONES_CRON` | `0 0 19 * * *` | Horario del job |
| `MERCADO_FUENTES_EXTERNAS` | `true` | `false` apaga las fuentes reales (los tests lo hacen) |
| `MERCADO_DOLAR_API_URL` | `https://dolarapi.com/v1` | |
| `MERCADO_PIZARRA_ROSARIO_URL` | `https://www.cac.bcr.com.ar/es/precios-de-pizarra` | |
| `MERCADO_TIMEOUT` | `15s` | Tiempo máximo por fuente |
