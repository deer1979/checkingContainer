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

## Aclaraciones del usuario (jun 2026) — importantes para el diseño
- Los **2 sensores de presión** y las **2 pinzas de temperatura** son del MISMO
  modelo y NO tienen serial que los distinga. El equipo/app los ve como "1" y "2";
  **el técnico selecciona manualmente** cuál es cuál (alta/baja, posición 1/2).
  ⇒ En la UI: asignación manual presión1/2 → alta/baja y temp1/2 → posición.
  Mapea directo a sensor1Reading / sensor2Reading del parser.
- **Temperatura en CENTÍGRADOS** (no convertir a °F).
- **Superheat / Subcooling = matemática interna** con tabla PT del refrigerante:
  de la presión medida + tipo de gas (R-134a, R-404A, R-407C, R-22, etc.) se
  obtiene la temperatura de saturación; la diferencia con la temp medida da
  sobrecalentamiento (línea de succión) o subenfriamiento (línea de líquido).
  ⇒ Necesitaremos tablas PT por refrigerante (o ecuaciones de saturación) en el
  módulo de cálculo. La app oficial lo hace en MeasureCalcUtil.java (referencia).

## Auto-cero del TITANMAX (aclaración usuario, jun 2026) — IMPORTANTE
- El TITANMAX **se auto-calibra**: a presión atmosférica marca **0** y de ahí
  sube. ⇒ El valor difundido es **presión MANOMÉTRICA ya lista** (referida a la
  atmósfera). NO restar atmosférica ni convertir desde absoluta.
- Ojo: en el APK existe `calculatePressureFromPSIA` (PSIA = absoluta), pero según
  el usuario el dato del advertising ya viene relativo (cero a la atmósfera).
  ⇒ Tomar sensor1/sensor2 tal cual como presión manométrica. CONFIRMAR igualmente
  con la lectura real (pantalla del Titan vs sensorN) al validar unidades.

## Manométrica vs absoluta para cálculos (aclaración usuario, jun 2026)
- MOSTRAR/GUARDAR en el reporte: presión MANOMÉTRICA (la del auto-cero, lo que ve
  el técnico).
- CALCULAR (saturación / superheat / subcooling): convertir a ABSOLUTA =
  manométrica + atmosférica (~14.696 PSI nivel del mar; idealmente ajustable por
  altitud). Con la absoluta se entra a la tabla PT del refrigerante. Esto explica
  `calculatePressureFromPSIA` del APK.
- Flujo: leer manométrica → +atmosférica → absoluta → tabla PT del gas → temp de
  saturación → comparar con temp medida (pinza) → superheat (succión) /
  subcooling (líquido).

## Cálculos y tabla de refrigerantes (extraído del APK, jun 2026)
- **Tabla PT completa extraída**: `feature_sensores_ref/refrigerant_data.json`
  (del APK, `R.raw.refrigerant_data`). **131 refrigerantes** (R-134a, R-404A,
  R-410A, R-22, R-407C, R-507A, etc.), cada uno con `liqSat[128]` y `vapSat[128]`
  (saturación líquido/vapor) + ejes `vapSatPressures`/`liqSatPressures` (128 ptos,
  0..~385 PSI). Campo `flammable` por gas. Con esto se obtiene la temp de
  saturación para una presión dada (interpolando en el array) → base de
  superheat/subcooling. (Datos termodinámicos = hechos físicos; para producción
  se pueden regenerar de fuentes públicas tipo CoolProp/NIST si se prefiere.)
- **Lógica de cálculo de referencia**: `MeasureCalcUtil.java` del APK. Métodos
  clave: `calculatePressureFromPSIA/PSIG`, `calculatePressureAtCurrentElevation`,
  `calculatePressureForGivenAlt` (¡ajuste por ALTITUD de la atmosférica!),
  `getCompensatedPressure`, `calculateVacuum`, `calculateSuperheatTarget`,
  `calculateTemperature`. Portar a Kotlin en el módulo de sensores.
- **Vacuómetro**: a presión atmosférica marca ~**760,000 micrones** (=760 mmHg);
  vacío profundo objetivo ~500 micrones. Confirma unidad = micrones.

## Diseño acordado con el usuario (jun 2026)
- **Registro continuo**: mientras dura la inspección, captar una lectura cada
  1–5 min (configurable) de los sensores presentes y guardarla como serie temporal
  en el estimado/PTI → adjuntar la tendencia al reporte/PDF. NO hace falta abrir
  YJACK VIEW (los datos llegan por advertising).
- **Selector manual** presión1/2 → alta/baja y temp pinza1/2 → posición (no hay
  serial que las distinga).
- Pantallas nuevas: una para "Mediciones del equipo" (escaneo + lecturas en vivo +
  registro) integrada al flujo del estimado. El usuario compartirá una ficha de
  estimado de ejemplo para diseñar dónde encajan.
- Refrigerante seleccionable por el usuario (lista de los 131) para los cálculos.
