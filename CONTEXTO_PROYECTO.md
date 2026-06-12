# Contexto técnico del proyecto — memoria para Claude

> **Leer esto primero en cada sesión nueva.** Brief escrito por/para un ingeniero
> Android senior (Kotlin + Jetpack Compose). Evita re-explorar el repo.
> Última actualización: jun 2026, tras commits `1e8928a`…`3b83c79`.

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
