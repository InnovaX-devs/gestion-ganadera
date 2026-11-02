# 0004 — App móvil offline: PWA

**Estado:** aceptada · **Fecha:** 2026-10-31 · **Issue:** #30

## Contexto

En el campo casi nunca hay señal, y es donde se registran los eventos (nacimientos, tratamientos,
pesajes, movimientos). La 0001 dejó la tecnología del móvil para M5: React Native/Expo con SQLite,
o una PWA.

Lo que pide el issue: cargar eventos sin conexión, sincronizar al volver la señal (`/api/sync`),
resolver conflictos y mostrar qué falta sincronizar.

## Opciones

1. **React Native / Expo + SQLite.** App nativa con base local real.
   - A favor: SQLite es robusto y sin límites prácticos; sincronización en segundo plano; acceso a
     Bluetooth (lectores de caravanas electrónicas), GPS en segundo plano y notificaciones.
   - En contra: una segunda base de código (pantallas, formularios y validaciones de nuevo);
     publicar y mantener en Google Play y App Store (cuentas, revisiones, demoras); el equipo no
     tiene experiencia en React Native.
2. **PWA**: la web actual instalable, con service worker e IndexedDB.
   - A favor: una sola base de código (React, Vite, TanStack Query, los mismos formularios y
     permisos); sin tiendas, se instala desde el navegador y se actualiza sola; el backend ya está
     preparado (ids UUID generados en el cliente, `operacionId` idempotente).
   - En contra: en iPhone, Safari puede borrar los datos de una PWA que no se abre por semanas; no
     hay sincronización en segundo plano con la app cerrada; sin Bluetooth en iPhone.

## Decisión

**PWA**, dentro de `/web` (no hace falta la carpeta `/mobile` de la 0001).

- Datos locales en **IndexedDB**: animales, lotes, rodeos, productos y eventos del establecimiento
  activo, y la cola de operaciones pendientes.
- **Service worker** para que la app abra sin conexión.
- Se sincroniza al abrir la app, al recuperar la señal, después de cargar algo y cada pocos minutos
  mientras está abierta. No hay sincronización con la app cerrada.
- Se pide almacenamiento persistente (`navigator.storage.persist()`) para que el navegador no borre
  los datos, y la app avisa si hay pendientes viejos sin enviar.

El protocolo de sincronización y la resolución de conflictos están en [`../sync.md`](../sync.md).

## Consecuencias

- La carga offline reutiliza los diálogos de eventos que ya existen.
- Hay que probar en Android (Chrome) y en iPhone (Safari, "Agregar a pantalla de inicio").
- **Revisar esta decisión** si aparece alguna de estas necesidades: lectores de caravanas por
  Bluetooth en iPhone, sincronización con la app cerrada, o pérdidas de datos en iPhone en la
  práctica. El protocolo de `/api/sync` no depende de la tecnología: una app Expo lo usaría igual.
