# Protocolos BLE multimarca — extraído de measureQuick (jun 2026)

> Ingeniería inversa del APK **measureQuick** (app híbrida Cordova; lógica en JS:
> `assets/www/build/main.js`). measureQuick es el "diccionario universal" de
> instrumentos HVAC: soporta muchas marcas. Esto permitiría que CheckingContainer
> NO quede atado a Yellow Jacket. La mayoría de marcas usan **GATT (conectar +
> notificaciones)**, a diferencia del advertising de Yellow Jacket.

## Funciones base de conversión (comunes)
```js
hex2float(A){ let g=parseInt(A,16), I=(g>>23&255)-127;
  return (2147483648&g?-1:1)*(1+(8388607&g)/8388607)*Math.pow(2,I) } // IEEE-754 32-bit
hex2dec(A){ return parseInt(A,16) }
```

## Marcas y cómo se leen
### Fieldpiece (Job Link Probes) — GATT, Nordic UART
- Nombre BLE: "Fieldpiece Job Link Probes" / "JB Probes"; código `jb`/`fp`.
- Service Nordic UART `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
  (TX `...0001`, RX `...0002`, gen `...0003`). Datos por notificación.
- Parser: consolidateFieldpiece().

### Yellow Jacket (sondas YJACK) — ya resuelto aparte (advertising)
- Código `yj`. Ver PROTOCOLO_YJACK_BLE.md (advertising, sin conexión).

### Testo Smart Probes (560/570/700…) — GATT
- Nombre "Testo Smart Probes"; Service/Char `5939D0C0-44EC-11E5-88B6-0002A5D5C51B`.
- Temp = hex2float(bytes 23,22,21,20) en °C; Presión = hex2dec(bytes 15,14).
- Parser: consolidateTesto(). Datos en hexMeas[].

### NAVAC (NSM1/NSH1/NSP1/NX1/NX4) — GATT + autenticación
- Nombre "NAVAC"; Service `0460657C-7E40-45D5-8AD3-279728E3C88E`.
  ⚠️ (mismo UUID que la pinza de temp YJ TempClamp — distinguir por nombre).
- Requiere authenticate() handshake; listenForNSM1Data / listenForHygrometerData.
- Presión cond = (hex2dec(b19,b18)-35535)/1000 ; evap = (hex2dec(b21,b20)-35535)/1000.
- Temp = hex2float(b29,b28,b27,b26) °C (+ offset por sonda).
- Parser: consolidateNAVAC().

### Sauermann Smart Probes (S440/S450) — GATT
- Services `7c8208b2-94de-11e5-...` y `49efe9f2-94e6-11e5-...`;
  Char `82a395cc-94e7-11e5-...`. hex2float 4 bytes (estilo Testo).

### CPS Link Probes (ABM-200/SPM-100/CCW/BTM-200) — GATT
- Nombre "CPS Link Probes"; Service `0000fcfd-...` (fe60-like); MTU 512.
- Parser: consolidateCPS().

### Sporlan (VFP/ProLink) — GATT
- Pro: `0117CB56-3EE8-41B8-9482-1DDC7D6F5FB8` (⚠️ = TITAN temp UUID YJ;
  distinguir por nombre/contexto). Legacy: `3bbd9d81-c3ed-41b5-a52a-460bd25d213e`.

### UEi / Seitron / CO monitors — GATT por nombre
- "UEi HUB/HAC/DL599", "Seitron Manifold", "CO Monitor/COA2".

## Implicación para CheckingContainer
- Arquitectura del módulo de sensores con **drivers por marca** (interfaz común
  `SensorDriver` → cada marca su detección + parser), igual que el "consolidateX"
  de measureQuick.
- Diferencia clave de transporte: YJ = advertising (escanear); el resto = GATT
  (conectar + suscribir notificaciones). El módulo debe soportar ambos.
- Empezar por Yellow Jacket (ya resuelto, es el equipo del usuario). Añadir otras
  marcas es incremental cuando el usuario las tenga.

## Recursos gráficos reutilizables de measureQuick (jun 2026)
El APK trae 485 imágenes; clave para nuestra UI de detección de dispositivos:
- `assets/www/assets/img/tool-pins/` → **93 íconos**: uno por INSTRUMENTO
  (testo-557s, testo-550i, navac-nx4, navac-nsm1, cps-btm200, jb-manometer,
  fp-sman, bluvac-pro, sauermann-th4, sporlan-pressure, seitron-temp-clamp...)
  + logos `brand-*` por marca (brand-yj, brand-fp, brand-testo_logo, etc.).
- `assets/www/assets/img/*_logo.png` → logos de marca a mayor tamaño.

### Idea de UI (recordatorio del usuario)
- La pantalla de mediciones lleva un **botón de Conexión** que escanea y
  **detecta marca/modelo** del dispositivo; mostrar el ícono de la marca/equipo
  detectado (reutilizando estos gráficos) hace la UX mucho más clara.

### ⚠️ Nota legal antes de copiar al repo
Los logos de marca (Testo, Yellow Jacket, etc.) y los íconos de producto son
material con copyright/marca registrada de measureQuick y de cada fabricante.
NO copiarlos al repo a la ligera. Opciones limpias:
1. Usar SOLO los de las marcas que el usuario realmente posee/usa, o
2. Crear íconos genéricos propios (un manómetro, una pinza, un vacuómetro
   dibujados nosotros) que no infrinjan marcas, y mostrar el nombre del modelo
   en texto. Para una v1 con Yellow Jacket, basta texto + ícono genérico.
Decidir con el usuario cuando construyamos la UI.
