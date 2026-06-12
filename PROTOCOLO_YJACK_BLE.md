# Protocolo BLE Yellow Jacket YJACK — DESCIFRADO (jun 2026)

> Ingeniería inversa del APK oficial **YJACK VIEW** (`com.ritchieengineering`).
> Clases clave: `blelib/utils/BleAdvertismentUtil.java`, `blelib/model/DeviceReading.java`,
> `blelib/utils/ByteUtil.java`. **No requiere conexión ni emparejamiento** ni
> capturar btsnoop: los sensores DIFUNDEN los datos en el BLE advertising
> (Service Data por UUID). La app solo escanea y parsea. Varias apps pueden
> escuchar a la vez (no compite con la app oficial).

## Cómo leerlo (Android)
1. `BluetoothLeScanner.startScan` con `ScanFilter` por los Service Data UUID de abajo.
2. En cada `ScanResult`: `scanRecord.getServiceData(ParcelUuid(UUID))` → `byte[]`.
3. Parsear ese byte[] como **LITTLE_ENDIAN** según la fórmula del sensor.

## Service Data UUIDs (identifican el tipo de sensor en el advertising)
- TITANMAX presión  `8b2a2afb-e67d-44b8-9985-7c164352e411`  (YJTITANP)
- TITANMAX temperat. `0117cb56-3ee8-41b8-9482-1ddc7d6f5fb8`  (YJTITANT)
- Amperímetro YJAMP  `3d34ca72-2165-4e40-ad6b-e6608ca5080e`
- Vacuómetro YJVac   `b94aeb61-aa02-4a8e-a6a3-39bf61b1c9f6`
- Pinza temp YJTC    `0460657c-7e40-45d5-8ad3-279728e3c88e`  (TempClamp)
- Manómetro YJMano   `bf9e01ed-b082-4f2f-a9b4-b1ccf52fe102`
- P51 presión        `85310c03-1608-4167-9f21-e81a42334674`
- P51 temperatura    `1e368999-7899-4f6f-9725-0c9ccba9e3db`
- YJPressure         `3b10ccad-37aa-4cc0-8553-574cb4933023`
- Anemómetro YJFLOW  `a7b13475-3481-4f12-b48e-36d126203465`
- Humedad RH         `1f38b6be-6439-4173-8625-5055d67c4718`
- commonService(ver) `0000180a-...` (string UTF-8 = versión firmware)

## Cabecera común del byte[] (DeviceReading.parseFromBytes), LITTLE_ENDIAN
- byte[0]: batería. `n = byte0 & 0xFF`; `batteryStatus = (n/16)*10`;
  `linkBatteryStatus = (n%16)*10`. (caso YJEXT distinto, no aplica a tus sensores).
- byte[1]: `readingIncrement`.
- bytes[2..5]: `int32 LE` = número de serie del sensor (con prefijo "P" en
  TITAN/P51). También sirve para el nombre `TITAN-####-####` (ver getDeviceName).
- Desde byte[6] en adelante: lecturas según tipo (abajo). 32767/3276.7 = "sin dato".

## Fórmulas por sensor (sensor1..4 = valores físicos)
### TITANMAX presión (YJTITANP) y P51 presión  → parseP51VacTitanP()
- sensor1 = int16_LE / 10.0   → **presión 1 (PSI)**
- sensor2 = int16_LE / 10.0   → **presión 2 (PSI)**
- sensor3 = SFLOAT(2 bytes)   → **temperatura interna / saturación**
### TITANMAX temperatura (YJTITANT)  → cae en parseGeneric (no TempClamp/YJPressure)
- sensor1 = int16_LE / 10.0   → **temperatura (°)**
- sensor2 = int16_LE / 10.0
### Amperímetro (YJAMP)  → parseYjAmp()
- sensor1 = int16_LE / 10.0   → **corriente (A)**
- sensor2 = int16_LE / 10.0   → **(2º canal / tensión?)**
- sensor3 = int16_LE * 1000
### Vacuómetro (YJVac)  → MISMO case que P51/TitanP (parseP51VacTitanP)
- sensor1 = int16_LE / 10.0
- sensor2 = int16_LE / 10.0
- sensor3 = SFLOAT(2 bytes)   → **vacío (micrones)** — confirmar unidad con lectura real
### Pinza de temperatura (TempClamp) y YJPressure  → parseGeneric()
- (salta primer short) ; sensor2 = int16_LE / 10.0   → **temperatura**
### YJMano  → parseYjMano(); Anemómetro → parseAnemometer(); SuperVac → parseYjSupervac*()

## SFLOAT = IEEE-11073 16-bit (ByteUtil.bytesToFloat)
```
mantisa  = signedFromUnsigned( b0 + ((b1 & 0x0F) << 8), 12bits )
exponente= signedFromUnsigned( (b1 >> 4), 4bits )
valor    = mantisa * 10^exponente
```
Especiales: b1==0x07 & b0==0xFE → +Inf ; 0xFF → NaN ; b1==0x08 & b0∈{00,01}→NaN ;
0x02 → -Inf.

## PENDIENTE para cerrar (mínimo, ya no es bloqueante)
Confirmar con UNA lectura real anotada qué `sensorN` es alta/baja/sat y las
unidades exactas (PSI vs bar, micrones, A). Basta abrir YJACK VIEW, mirar la
pantalla y comparar con sensor1/2/3. El grueso (UUIDs + estructura + fórmulas)
ya está resuelto desde el APK.

## Implementación en CheckingContainer
Módulo nuevo p.ej. `core/sensors` o `feature/sensors`: scanner BLE + parser
(portar estas fórmulas a Kotlin) → expone `Flow<LecturaSensor>`. La pantalla de
estimado/PTI añade sección "Mediciones del equipo": botón escanear → snapshot de
presiones/temps/vacío/amperaje con timestamp → persistir en Estimado y volcar al PDF.
minSdk 26 ya soporta BLE scan; permisos BLUETOOTH_SCAN/CONNECT (Android 12+) +
neverForLocation. No requiere Firebase ni nube.
