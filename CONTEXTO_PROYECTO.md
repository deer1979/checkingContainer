# Contexto técnico del proyecto — memoria para Claude

> **Leer esto primero en cada sesión nueva.** Brief escrito por/para un ingeniero
> Android senior (Kotlin + Jetpack Compose). Evita re-explorar el repo.
> Última actualización: jun 2026, tras commits `1e8928a`…`80e8ecc`.
>
> ⚡ **LO MÁS RECIENTE (no re-explorar): el feature de sensores BLE YA ESTÁ
> IMPLEMENTADO.** Ver la sección **"Feature de sensores BLE — ESTADO ACTUAL"**
> más abajo. Las secciones posteriores ("Idea futura", "Visión ampliada",
> "Corrección de topología") son el trasfondo de investigación que llevó a esa
> implementación; consúltalas solo si necesitas el porqué, no para saber qué hay.

## Qué es
App Android **offline-first** para inspección de contenedores refrigerados (reefers).
UI y commits 100% en **español**. Un solo dev/propietario; flujo git: **push directo
a `main`, sin PRs** (ver CLAUDE.md). CI publica APK debug como release `latest-debug`.

## Stack
Kotlin 2.3.20 (K2) · Compose M3 (BOM 2026.05) · Room 2.8.4 (BD v14) · Hilt 2.59 ·
Navigation Compose + SharedTransitionLayout · CameraX + ML Kit (OCR) · Coil 3 ·
Firebase Firestore/Storage · DataStore · Gradle 9.4 + convention plugins en
`build-logic/` · JDK 21 · minSdk 26 / target 36 · versión 0.3.0 (code 2).
⚠️ Las vars `SUPABASE_URL`/`SUPABASE_ANON_KEY` (BuildConfig/CI) son **Firebase**
mal nombrado; vacías ⇒ modo solo-local.

## Arquitectura (multi-módulo)
- `app/` — MainActivity (splash, edge-to-edge), `MainViewModel` (auth/theme,
  `WhileSubscribed`), shell: `CheckingContainerApp` → `PublicShell` (splash/login) o
  `AuthenticatedShell` (Scaffold + `AppBottomBar` pill + NavHost). `ShellViewModel`
  expone badges y **mantiene viva la sync de digitación** (`digitacionSync`,
  colectada con lifecycle → listener Firestore solo en foreground).
- `core/model` — data classes inmutables (User con roles SuperAdmin/Admin/Editor/Viewer,
  Inspection, ReeferUnit, Estimado v2 con `damages: List<DamageItem>`, Announcement…).
  Declaradas estables en `compose-stability.conf` (NO vaciar ese archivo).
- `core/database` — Room, migraciones 4→14 en `migrations/`. `AnnouncementDao` tiene
  `replaceAllDiff` (upsert + DELETE NOT IN; no usar `replaceAll`, parpadea) y
  `observeUnreadCount(seen)` para el badge.
- `core/data` — repos `*Impl` + `FirestoreService` (sync) + `StorageService` (fotos).
  Patrón: write a Room primero, luego upsert a Firestore (errores solo se loguean).
  `AnnouncementsRepositoryImpl.refreshFromRemote()` tiene throttle de 5 min.
- `core/domain` — interfaces + `AuthState` sealed.
- `feature/units` — el módulo grande: lista, detalle (timeline 2+confirmación),
  entry, búsqueda, **escáner OCR** (`TextRecognitionAnalyzer` + `ProjectionCharDetector`
  + `Iso6346`; los bitmaps por frame SE RECICLAN — regla: nada que entre a
  `recognizer.process()` se recicla antes de sus listeners), **estimados**
  (`EstimadoScreen` = LazyColumn con ítems por key y UN par de photo-launchers
  compartidos a nivel pantalla con `rememberSaveable`; `EstimadoPdfGenerator` corre
  en `Dispatchers.Default`, fotos a 700px; `PdfPreviewSheet` con
  `sheetGesturesEnabled = false` para que no rebote).
  **Fotos por ítem (jun 2026): `DamageItem` guarda `damagePhotos`/`repairPhotos`
  como LISTAS (antes era una sola foto por lado), máx `MAX_FOTOS_POR_GRUPO`=6.
  Migración perezosa en `EstimadoEntity.parsePhotos` lee el formato viejo (campo
  único `damagePhoto`/`repairPhoto`) y el nuevo (array) sin perder datos; los
  damages se sincronizan como string JSON, así que NO hubo migración de esquema
  Room. Storage usa ruta única por foto (sufijo UUID). UI: galería horizontal
  (`LazyRow`) por grupo con miniaturas + botón agregar; PDF en cuadrícula 3/fila.**
- `feature/{login,announcements,users,admin,settings,splash}` — login email+PIN 6
  dígitos; anuncios con adjuntos; users CRUD (admin); settings con tema.
- `MyApplication` implementa `SingletonImageLoader.Factory`: Coil con memCache 20% +
  diskCache 128MB (clave para fotos offline).

## Convenciones que ya sigue el código (mantener)
- StateFlow con `stateIn(viewModelScope, WhileSubscribed(5_000), inicial)` +
  `collectAsStateWithLifecycle` en UI.
- LazyColumn siempre con `key` + `contentType`.
- Badges del shell se colectan DENTRO de la lambda `bottomBar` (no subirlos al cuerpo).
- `SimpleDateFormat` como `private val` top-level por archivo, nunca por ítem.
- Compilar con `./gradlew :app:compileDebugKotlin` antes de cada commit.
  En contenedor remoto: instalar SDK en `/opt/android-sdk` y crear
  `local.properties` con `sdk.dir=/opt/android-sdk` si no existe.

## Seguridad y sync (desde jun 2026)
- PIN: hash SHA-256+salt (`PinHasher` en core/common, formato `v1:salt:hash` en la
  columna `pin`); migración perezosa en login. Al editar usuario, PIN vacío = mantener.
- FirestoreService: todos los writes pasan por `write(op){...}` con timeout de ack
  de 10s (sin conexión NO se cuelga; el SDK re-envía solo) y registran estado en
  `SyncStatusRepository` (DataStore) → visible en Ajustes.
- Fotos: comprimidas a JPEG 80 / máx 1600px con rotación EXIF antes de subir
  (`compressForUpload` en EstimadoViewModel).

## Tests
27 unit tests (`./gradlew testDebugUnitTest :core:model:test`, obligatorios en CI):
Iso6346, correctContainerChars/majorityVote, EstimadoTotals (única fuente del
cálculo de totales/IVA — usar SIEMPRE esta, no recalcular inline), PinHasher.
Pendiente: tests de DAO/migraciones (requieren emulador).

## Deuda restante (detalle en `PLAN_DEUDA_TECNICA.md`, sección "Pendientes restantes")
Baseline Profiles (receta para PC en el plan), tests de DAO, strings a recursos
(boy-scout), paginación historial si hace falta. Docs: `FIREBASE.md` es la guía del
backend (los nombres SUPABASE_* son legado intencional — no renombrar sin coordinar).

## Historia reciente (jun 2026)
Optimización de rendimiento en 3 fases + pasada de deuda técnica, todo en `main`:
listener Firestore con lifecycle, caché Coil, anuncios sin parpadeo,
EstimadoScreen→LazyColumn, fix OOM del OCR, PDF en background, skeletons,
fix rebote del preview PDF, PIN hasheado, sync visible, 27 tests, CI bloqueante.

## Adaptativo y agentes (jun 2026)
- Shell adaptativo: `AuthenticatedShell` usa window size class — pill inferior en
  Compact, `AppNavigationRail` en Medium/Expanded (tablets). NavHost compartido.
- AppFunctions (`androidx.appfunctions` 1.0.0-alpha09; alpha10 existe pero
  `appfunctions-service` solo llega a alpha09 — mantener los 3 artefactos parejos):
  `app/.../appfunctions/ContainerFunctions.kt` expone `consultarContenedor` y
  `resumenEstimadosAbiertos` a agentes/sistema (Android 16+). Registradas vía
  `AppFunctionConfiguration.Provider` en MyApplication (factory Hilt).
  Probar en dispositivo: `adb shell cmd app_function list-app-functions`.
- Styles API: DIFERIDA (exige Compose foundation 1.12, aún en beta02; el BOM
  estable 2026.06.01 trae 1.11.4); decisión documentada en PLAN_DEUDA_TECNICA.md.
- **IA local (Gemini Nano, jul 2026)**: `mlkit-genai-prompt 1.0.0-beta2`.
  Detección real en Ajustes (`AiCoreAvailability.kt`, checkStatus). Respaldo de
  OCR en `GeminiNanoOcr.kt` (feature:units): cuando el usuario dispara la captura
  y ML Kit no lee (vertical sucio, placa de datos), el frame pasa a Gemini Nano
  que SOLO transcribe; la salida se valida con los mismos filtros duros del OCR
  (correctContainerChars + Iso6346.isValid; regex de parseDataPlate) — nada
  inventado puede pasar. En equipos sin IA todo devuelve null y el flujo previo
  queda intacto (vertical: modo prueba con texto crudo). Gancho en
  `TextRecognitionAnalyzer.onNanoFallback` + `nanoFinished()` para reanudar;
  cableado e indicador "Leyendo con IA local…" en `OcrScannerBottomSheet`.
  Probado en Xiaomi 17 Ultra (estado "Disponible").

## Catálogo de clientes (jul 2026)
Entidad `Client` (razón social, RUC/cédula/pasaporte, email, dirección,
teléfono, contacto, notas) con validación por dígito verificador SRI
(`IdentificacionEc` en core:model, con tests — módulo 10 cédula, módulo 11
sociedades/público, RUC natural = cédula+establecimiento). Tabla `clients`
(migración 15→16, que también añade clientId + snapshot del cliente al
estimado) + colección Firestore + bootstrap. UI en feature:units: sección
Clientes (Ajustes → Catálogos → Clientes; `clientesGraph`), y en el estimado
selector con búsqueda + creación rápida (`ClientPickerSheet`, formulario
compartido `ClientFormFields`). El estimado congela snapshot (clientIdNumber/
dirección/teléfono/email) al asignar; el PDF imprime RUC/dirección/teléfono.
Cualquier usuario puede crear clientes. Fase 3 HECHA: `ClientDataExtractor`
(pegar texto WhatsApp o foto de factura desde galería → pre-llenado del
formulario vía `ClientAssistRow`, presente en formulario y creación rápida).
Capas anti-inventos: Gemini Nano con prompt estricto → RUC/cédula SIEMPRE
revalidados con IdentificacionEc → extractor determinista por regex como
respaldo/sin-IA (OCR ML Kit para fotos) → el usuario revisa antes de guardar.
PENDIENTE menor: botón de captura directa con cámara en el formulario
(hoy: tomar foto con la cámara del sistema y elegirla de galería).

## Equipos más allá del reefer (jul 2026 — tanda 1 HECHA)
`TipoEquipo` (REEFER/AIRE_ACONDICIONADO/CAMARA_FRIA/CHILLER/OTRO) en
ReeferEquipment/entity (migración 16→17) + Firestore. El "+" de la lista abre
un ASISTENTE ("¿Qué vas a revisar?"); para no-reefer la identidad es un
"código de equipo" libre (≥3 chars, editable, ocupa el lugar del containerNo
→ inspecciones/estimados/sensores/PDF fluyen sin tocarse), sin validación ISO
ni escáner de número, y PTI no exigido. Rótulos por heurística: containerNo
que NO pasa Iso6346 ⇒ "Equipo:"/"Código de equipo" en PDF y estimado.
Tanda 2 HECHA: `PlacaEquipoExtractor` + `DatosPlacaEquipo` (@Generable:
marca/modelo/serie/refrigerante/capacidad/voltaje/año) — foto por cámara
(TakePicture + FileProvider) o galería en `PlacaScanRow` (solo no-reefer,
sobre IdentificationCard); pre-llena vía el canal OcrResult (claves nuevas
"Manufacturer" y "Observaciones" en applyOcrFields; refrigerante/capacidad/
voltaje van a observaciones) y sugiere código = prefijo(AC/CF/CH/EQ)-SERIE
sin pisar un código ya escrito. Sin IA: OCR + regex por palabras clave.
Nota: se usa la cámara del sistema (sin overlay de encuadre); un visor
propio con marco queda como mejora opcional. Etiqueta de tipo en listas:
descartada — el código (AC-…, CF-…) ya distingue a simple vista.
Estados por tipo (jul 2026): InspStatus ganó MANT_PREVENTIVO/MANT_CORRECTIVO/
REPARACION (colores reusan OP/NEST/EST). No-reefer muestra SOLO esos 3 chips
(default PREVENTIVO), sin selector PTI; REPARACION dispara el estimado igual
que EST. Contexto de negocio: contratos/licitaciones de mantenimiento.
PENDIENTE: ficha técnica completa de placa (lista abierta etiqueta→valor
extraída por IA, columna nueva en reefer_units, visible en tarjeta + PDF;
plan acordado con el usuario — "no encasillar la placa en campos fijos").

## ML Kit R41 / Structured Output (jul 2026)
genai-prompt beta3 + genai-schema(-compiler) alpha1 (KSP). La extracción de
clientes usa SALIDA TIPADA: `DatosClienteExtraidos` anotada con
@Generable/@Guide (debe ser pública — el provider KSP generado es público);
`generateTypedContentRequest(base, DatosClienteExtraidos::class)` devuelve el
objeto directo, sin parseo. `SystemInstruction` lleva las reglas anti-inventos.
Requiere Nano v3+: si el equipo no lo soporta, cae solo al formato de líneas
(camino previo, intacto) y luego a regex/OCR. Ideas futuras del R41:
multi-imagen por sesión (Content multipart) y genai-speech-recognition
(dictar descripciones de daño). Thinking Mode descartado (latencia sin
beneficio para extracción).

## Entorno remoto (nota operativa)
Si el contenedor se recicla: reinstalar SDK (`/opt/android-sdk`, plataformas
36 + 37.0, build-tools 36) con cmdline-tools de dl.google.com, recrear
`local.properties`, y si el wrapper de Gradle no puede bajar su distribución
(github.com bloqueado), usar el espejo
`mirrors.cloud.tencent.com/gradle/gradle-X-bin.zip` y reconstruir
`~/.gradle/wrapper/dists/<ver>/<hash>/` (zip + extraído + marcador `.ok`).

## Sync post-login (jul 2026)
`BootstrapRepository.syncRecentAsync(user)` — disparado por `MainViewModel` en
cada transición a `AuthState.Authenticated` (distinct por user.id), corre en
`@ApplicationScope` (no muere al navegar). Estrategia de costo mínimo:
2 consultas (estimados ABIERTOS + `createdAt > 24h`, ambas single-field, sin
índice compuesto) + reconciliación puntual de cierres (abiertos locales que ya
no están abiertos en remoto → fetch por id, 0-2 docs típico; si el doc no
existe en remoto — creado offline sin subir — no se toca). Filtro por rol en
cliente: Admin/SuperAdmin ven todo, técnico solo `technicianId == user.id`.
El bootstrap completo sigue siendo solo para Room vacío (primera instalación).

## Actualización de versiones (jul 2026)
- compileSdk **37** (plataforma `android-37.0`; targetSdk sigue en 36 a propósito —
  subirlo cambia comportamiento runtime y requiere probar en dispositivo).
- Al día: Kotlin 2.3.21, Compose BOM 2026.06.01, Hilt 2.60.1, lifecycle 2.11.0,
  coroutines 1.11.0, core-ktx 1.19.0, Coil 3.5.0, Firebase BOM 34.15.0,
  google-services 4.5.0, MockK 1.14.11, AppFunctions alpha09.
- DIFERIDO: Gradle wrapper 9.6.1 (el contenedor remoto no puede descargar la
  distribución — bloqueo de red a github.com; hacerlo desde el PC), Kotlin 2.4.0
  (validar KSP antes), AGP 9.3.0 (esperar estable; hoy en rc02).

## Feature de sensores BLE — ESTADO ACTUAL (jun 2026, commits hasta `80e8ecc`)
**Implementado y en `main`.** Módulo **`feature/sensors/`** (la implementación viva;
si existe un viejo `feature_sensores_ref/` es solo material de referencia copiado de
los APK oficiales, no es código del app). Lectura de instrumentos Yellow Jacket YJACK
**por BLE ADVERTISING (sin conexión GATT)**: los sensores emiten Service Data y la app
los lee al vuelo.

Qué hay ya hecho:
- **`BleSensorScanner`** — escanea con `ScanSettings` (legacy=false, MATCH_AGGRESSIVE,
  CALLBACK_ALL_MATCHES) y **reinicia el escaneo cada 7 s** para evitar el congelamiento
  conocido de Android (mismo truco que la app oficial). Emite `SensorReading` por anuncio.
- **`YjackParser`** — parseo LITTLE_ENDIAN. ⚠️ La presión del anuncio es **ABSOLUTA
  (PSIA)** → `aPsig()` resta 14.696 para dar manométrica. La temperatura viene en **°F**
  → `aCelsius()`. Centinela "sin dato" = `SensorReading.SIN_DATO = 3276.7`. (Verificado
  contra lecturas reales del equipo.)
- **`SensorsScreen` / `SensorsViewModel`** — pantalla LIMPIA: sin barra inferior global
  (se oculta en `AuthenticatedShell` para rutas `sensors/`), sin encabezado; `BarraSuperior`
  con volver + identidad + estado + botón Bluetooth. Tarjeta **Corriente arriba**, luego
  **Presiones** y **Temperaturas** como `FilaAltaBaja`. Roles **ALTA (rojo) / BAJA (celeste)**
  se alternan por lectura con un chip (`toggleRol`). Muestreo cada 5 s al historial
  (5 tomas). `detener()` limpia las tarjetas.
- **Tabla PT incrustada + cálculos**: `res/raw/refrigerant_data.json` (131 gases extraídos
  de YJACK VIEW) → `RefrigerantRepo` la carga; `Saturacion.kt` interpola la temp de
  saturación e calcula **superheat** y **subcooling** (funciones puras, 6 tests en
  `SaturacionTest`). En pantalla: `SelectorRefrigerante` (desplegable, por defecto
  **R-134a**) + sección **"Saturación y rendimiento"** con dos tarjetas: Baja/Succión
  (sat. vapor + superheat) y Alta/Descarga (sat. líquido + subcooling).

HECHO (jul 2026) — **captura de mediciones al estimado**: botón "Capturar para
el estimado" en la pantalla de sensores (activo con ≥1 sensor conectado);
congela un `MedicionSnapshot` (PSIG/°C/A, SH/SC, refrigerante, dispositivos;
campos null = sin dato, captura parcial válida) y lo agrega al estimado
ABIERTO del contenedor (`EstimadosRepository.addMedicion`). Persistencia:
columna `mediciones` JSON en estimados (migración 14→15), campo espejo en
Firestore. UI: sección "Mediciones" en EstimadoScreen (borrable mientras esté
abierto — ojo: el borrado se persiste con el botón Guardar del estimado, la
captura desde sensores se persiste sola). PDF: sección "MEDICIONES DEL EQUIPO"
bajo los datos del equipo; sin mediciones la sección no aparece.

DIFERIDO (NO está hecho — pendientes reales del feature):
- Layout: hoy es una **sección separada** "Saturación y rendimiento". El usuario quería
  evaluar fusionar todo en **una sola columna por lado** (presión→temp→sat→SH/SC); quedó
  pendiente de decidir tras probar.
- **Auto-desconexión por inactividad** (timeout).
- **Corrección de altitud por GPS** (`gauge = (psia−14.7) + elev_ft×0.49/1000`).
- **Vacuómetro** (micrones) y **pinzas de temperatura** como sensores adicionales.
- **Persistir el snapshot de mediciones** en el Estimado/PTI e imprimirlo en el PDF.

### Idea futura del usuario: asistente de rendimiento (medido vs objetivo)
Lo que HOY mostramos (Superheat/Subcooling) es **MEDIDO/REAL = automático**, derivado
de las presiones+temps del equipo y la tabla PT. Falta su contraparte: el **OBJETIVO
(target)**. La idea del usuario (jun 2026, "solo una idea", aún sin implementar):
- Mientras la unidad hala a temperatura (p. ej. setpoint −18 °C), mostrar una **barra
  de rendimiento** que indique cómo el SH y SC **medidos** se acercan a su **objetivo**;
  cuando AMBOS entran en rango → la barra se pone verde y se **toma la muestra** (snapshot).
- ⚠️ El reto NO es el cálculo ni la UI, es **de dónde sale el objetivo**: en reefers no
  hay tabla universal tipo AC residencial; depende de **marca/modelo/refrigerante/setpoint**
  (lo fija la TXV/EEV del equipo). "El objetivo lo dicen los datos técnicos del fabricante."
- **SOLUCIÓN DEFINIDA por el usuario (jun 2026):** el objetivo lo **ingresa el técnico
  manualmente**, no lo deduce la app. La pantalla muestra un **aviso/ayuda** del tipo
  *"Consulta en el manual del equipo o en la web el superheat y subcooling objetivo para
  tu setpoint (p. ej. −18 °C) e ingrésalos aquí"*. Así NO importa marca/modelo: el dato
  técnico lo aporta el usuario una sola vez. ⇒ Campos **"SH objetivo"** y **"SC objetivo"**
  (con rango opcional) + el aviso; la app compara medido vs objetivo y pinta la barra.
  (Evolución opcional a futuro: recordar el objetivo por equipo/modelo para no re-teclear.)
- En la pantalla habrá que **etiquetar claramente "medido" vs "objetivo"** cuando exista
  el target (hoy solo está el medido).
- Referencia en el APK oficial: método `calculateSuperheatTarget` (MeasureCalcUtil).

---

## Idea futura: lectura automática del manómetro por Bluetooth (investigado jun 2026)
> Trasfondo histórico de la investigación. El resultado está arriba en "ESTADO ACTUAL".
Equipo del usuario: **Yellow Jacket TITANMAX** (P/N 40881), BLE, anuncia como
`TITAN-2503-5221` (MAC 60:B6:47:7A:75:9A). Objetivo: capturar presiones/temps
del PTI sin digitar.
- Conecta sin emparejar ni cifrado (acceso libre). App oficial: **YJACK VIEW**;
  measureQuick también lo integra (bajo acuerdo privado, protocolo NO público).
- Servicio de datos propietario: `1854edbe-c75c-47d7-92ed-71b5f80549f8`.
  Característica de datos (Notify+Read): `9592d325-ec81-411f-86b2-599984f27589`.
  Resto del servicio son WRITE (comando/respuesta). `1d14d6ee-...` = DFU Silicon
  Labs (¡NO tocar, ladrilla el equipo!).
- Es protocolo **comando/respuesta**: hay que enviar un comando de arranque (aún
  desconocido) a una característica WRITE para que empiece a notificar. Leer la
  característica sin comando devuelve fijo `1D-02` (estado, no presión).
- PENDIENTE para descifrarlo: capturar `btsnoop_hci.log` mientras corre **YJACK
  VIEW** (no nRF Connect). Orden CRÍTICO en Samsung: activar "Registro Bluetooth
  HCI" → reiniciar BT UNA vez ANTES → sesión con app oficial 2-3 min → informe de
  errores SIN volver a tocar BT (reiniciar BT borra el log). Primer intento salió
  vacío (1.1s, solo control) por reiniciar BT después de la sesión.
- ✅ PROTOCOLO DESCIFRADO del APK oficial → ver `PROTOCOLO_YJACK_BLE.md`.
  Datos van en BLE ADVERTISING (sin conexión). UUIDs y fórmulas de todos los
  sensores ya documentados. Solo falta confirmar unidades con 1 lectura real.
- Implementación eventual: módulo `feature/bluetooth` o `core/` con
  BluetoothGatt/companion device; integrarlo en el formulario de inspección/PTI.

## Visión ampliada del feature BLE (jun 2026)
El objetivo NO es solo el manómetro: capturar TODO el instrumental YJACK del
usuario dentro del estimado y del reporte de reparación, sin digitar:
- **TITANMAX** (manómetro): presión alta/baja + temps de saturación.
- **2 pinzas de temperatura** YJACK: según el log, el TITANMAX ya las integra y
  reenvía (quizá no requieran conexión aparte).
- **Vacuómetro** (micrones) YJACK: probablemente dispositivo BLE aparte.
- **Sensor de corriente / amperímetro** YJACK BLE.
Todos son de la familia YJACK → muy probable mismo protocolo BLE (servicio
`1854edbe...`, patrón comando/respuesta). Descifrar UNO sirve para todos.
Android soporta múltiples conexiones BLE simultáneas (como hace la app oficial).
Diseño propuesto: sección "Mediciones del equipo" en el formulario de
estimado/PTI con botón "Conectar instrumentos" → escanea/conecta los sensores
encendidos y captura un snapshot de lecturas (presión, temp, vacío, amperaje)
con timestamp; se persiste en el Estimado (nuevos campos / tabla de mediciones)
y se imprime en el PDF. Modelo: ampliar `Estimado`/`DamageItem` o nueva entidad
`Medicion` (sensorType, valor, unidad, timestamp).

## Corrección de topología BLE (jun 2026) — IMPORTANTE
El usuario aclaró la conexión real de los sensores:
- **TITANMAX es el HUB**: el **vacuómetro va conectado AL TITANMAX** (no es BLE
  propio) y las pinzas de temperatura también las integra el TITANMAX. ⇒ UNA
  sola conexión BLE al TITANMAX entrega presión + temperaturas + vacío juntos.
- **Sensor de corriente (amperímetro)**: es BLE INDEPENDIENTE, conexión propia
  directa al móvil (la app lo reconoce por separado). Segundo dispositivo.
⇒ Solo 2 conexiones BLE a descifrar: TITANMAX (presión+temp+vacío) y amperímetro.
Plan A para el protocolo = capturar btsnoop de la app oficial y descifrarlo
(NO esperar SDK del fabricante: es secreto industrial, improbable que lo den).
