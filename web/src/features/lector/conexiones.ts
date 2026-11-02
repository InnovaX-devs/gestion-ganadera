/**
 * Conexión directa con el lector, para los que no funcionan como teclado:
 *
 * - Serie (Web Serial): cable USB, o Bluetooth "clásico" emparejado como puerto COM. Chrome o Edge
 *   en computadora.
 * - Bluetooth de bajo consumo (Web Bluetooth): Chrome en Android y computadora.
 *
 * Las dos entregan texto: se junta hasta cada fin de línea y cada línea se pasa a `onLinea`.
 * iPhone no tiene ninguna de las dos: ahí solo anda el modo teclado.
 */

/** Servicios BLE "serie" conocidos: Nordic UART y el de los módulos HM-10 / genéricos (FFE0). */
const SERVICIOS_BLE = [
  {
    servicio: '6e400001-b5a3-f393-e0a9-e50e24dcca9e',
    notifica: '6e400003-b5a3-f393-e0a9-e50e24dcca9e',
  },
  { servicio: 0xffe0, notifica: 0xffe1 },
]

export const VELOCIDADES = [9600, 19200, 38400, 57600, 115200] as const

export const haySerie = () => typeof navigator !== 'undefined' && 'serial' in navigator
export const hayBluetooth = () => typeof navigator !== 'undefined' && 'bluetooth' in navigator

export interface Conexion {
  nombre: string
  cerrar: () => Promise<void>
}

/** Arma líneas con lo que va llegando (los lectores mandan de a pedazos). */
function porLineas(onLinea: (linea: string) => void) {
  let pendiente = ''
  return (pedazo: string) => {
    pendiente += pedazo
    // ETX (fin de texto) también corta: hay lectores que no mandan fin de línea
    // eslint-disable-next-line no-control-regex
    const partes = pendiente.split(/\r\n|\r|\n|\u0003/)
    pendiente = partes.pop() ?? ''
    for (const parte of partes) if (parte.trim()) onLinea(parte)
    // Algunos no mandan fin de línea: si lo pendiente ya es largo, se manda igual
    if (pendiente.length > 64) {
      onLinea(pendiente)
      pendiente = ''
    }
  }
}

export async function conectarSerie(
  velocidad: number,
  onLinea: (linea: string) => void,
  onCorte: () => void,
): Promise<Conexion> {
  const serial = (navigator as unknown as { serial: Serial }).serial
  const puerto = await serial.requestPort()
  await puerto.open({ baudRate: velocidad })

  const decodificador = new TextDecoder()
  const recibir = porLineas(onLinea)
  let activo = true
  let lector: ReadableStreamDefaultReader<Uint8Array> | undefined

  void (async () => {
    try {
      while (activo && puerto.readable) {
        lector = puerto.readable.getReader()
        try {
          for (;;) {
            const { value, done } = await lector.read()
            if (done) break
            if (value) recibir(decodificador.decode(value, { stream: true }))
          }
        } finally {
          lector.releaseLock()
        }
      }
    } catch {
      // Se desenchufó o se apagó el lector
    }
    if (activo) onCorte()
  })()

  const info = puerto.getInfo()
  return {
    nombre: info.usbVendorId
      ? `Lector USB (${info.usbVendorId.toString(16)})`
      : 'Lector por puerto serie',
    cerrar: async () => {
      activo = false
      await lector?.cancel().catch(() => undefined)
      await puerto.close().catch(() => undefined)
    },
  }
}

export async function conectarBluetooth(
  onLinea: (linea: string) => void,
  onCorte: () => void,
): Promise<Conexion> {
  const bluetooth = (navigator as unknown as { bluetooth: Bluetooth }).bluetooth
  const dispositivo = await bluetooth.requestDevice({
    acceptAllDevices: true,
    optionalServices: SERVICIOS_BLE.map((s) => s.servicio),
  })
  const gatt = await dispositivo.gatt?.connect()
  if (!gatt) throw new Error('No se pudo conectar con el lector')

  let caracteristica: BluetoothCaracteristica | undefined
  for (const { servicio, notifica } of SERVICIOS_BLE) {
    try {
      const s = await gatt.getPrimaryService(servicio)
      caracteristica = await s.getCharacteristic(notifica)
      break
    } catch {
      // Este lector no usa ese servicio: se prueba el siguiente
    }
  }
  if (!caracteristica) {
    gatt.disconnect()
    throw new Error(
      'Ese dispositivo no manda lecturas por Bluetooth de bajo consumo. Probá emparejarlo como teclado.',
    )
  }

  const decodificador = new TextDecoder()
  const recibir = porLineas(onLinea)
  const alRecibir = (e: Event) => {
    const valor = (e.target as unknown as BluetoothCaracteristica).value
    if (valor) recibir(decodificador.decode(valor, { stream: true }))
  }
  caracteristica.addEventListener('characteristicvaluechanged', alRecibir)
  await caracteristica.startNotifications()
  let activo = true
  dispositivo.addEventListener('gattserverdisconnected', () => {
    if (activo) onCorte()
  })

  return {
    nombre: dispositivo.name || 'Lector Bluetooth',
    cerrar: async () => {
      activo = false
      caracteristica?.removeEventListener('characteristicvaluechanged', alRecibir)
      gatt.disconnect()
    },
  }
}

// ---------- Tipos mínimos (TypeScript todavía no trae Web Serial ni Web Bluetooth) ----------

interface Serial {
  requestPort(): Promise<PuertoSerie>
}

interface PuertoSerie {
  open(opciones: { baudRate: number }): Promise<void>
  close(): Promise<void>
  readable: ReadableStream<Uint8Array> | null
  getInfo(): { usbVendorId?: number; usbProductId?: number }
}

interface Bluetooth {
  requestDevice(opciones: {
    acceptAllDevices: boolean
    optionalServices: (string | number)[]
  }): Promise<DispositivoBluetooth>
}

interface DispositivoBluetooth extends EventTarget {
  name?: string
  gatt?: { connect(): Promise<ServidorGatt> }
}

interface ServidorGatt {
  getPrimaryService(servicio: string | number): Promise<{
    getCharacteristic(c: string | number): Promise<BluetoothCaracteristica>
  }>
  disconnect(): void
}

interface BluetoothCaracteristica extends EventTarget {
  value?: DataView
  startNotifications(): Promise<unknown>
}
