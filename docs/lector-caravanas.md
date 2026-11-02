# Lector de caravanas electrónicas (#33)

El sistema acepta cualquier bastón lector de caravanas (Allflex, Tru-Test, Gallagher, etc.) de tres formas:

| Forma | Dónde anda | Cómo se usa |
|-------|-----------|-------------|
| **Modo teclado (HID)** | Todos los dispositivos, iPhone incluido | Emparejar el bastón por Bluetooth como teclado y leer. No hay que tocar nada en el sistema. |
| **Puerto serie** (Web Serial) | Chrome / Edge en computadora | Botón del lector en el encabezado → "Cable o puerto serie". Sirve para USB y para Bluetooth emparejado como puerto COM. |
| **Bluetooth de bajo consumo** (Web Bluetooth) | Chrome en Android y computadora | Botón del lector → "Bluetooth". Lectores con servicio Nordic UART o FFE0. |

## Cómo se reconoce una lectura

- `web/src/features/lector/lectura.ts` saca los 15 dígitos de la caravana (ISO 11784) de lo que mande el lector: con espacios, guiones, prefijos de texto, caracteres de control, bandera de animal adelante, y el peso si viene de una balanza (`982 000123456789,452.5`).
- En modo teclado, `teclado.ts` distingue al lector de una persona por la velocidad: todo llega en pocas decenas de milisegundos y termina en Enter.
- Tests: `npm test` (`web/tests/lectura.test.ts`). Si aparece un lector con otro formato, agregar el caso ahí.

## Qué pasa al leer

Cada pantalla que espera lecturas se registra con `useLectura`; la última abierta tiene prioridad:

- **Grilla de pesajes:** pone la caravana visual del animal en la fila; si vino el peso, lo completa y pasa a la siguiente.
- **Elegir animales** (tratamiento, movimiento, venta, pesaje grupal): suma el animal a la lista.
- **Alta o edición de animal:** completa la caravana electrónica.
- **Cualquier otra pantalla:** abre la ficha del animal. Si no está cargado, ofrece darlo de alta con la caravana ya puesta.

Si el cursor está en un campo que no espera lecturas (ej: un buscador), lo leído queda escrito ahí, como si se hubiera tipeado.

Sin conexión busca en los animales guardados en el dispositivo (#30).
