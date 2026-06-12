# Plan de deuda técnica — checkingContainer

> Generado tras la optimización de rendimiento (commits `1e8928a` → `e054214`).
> **ACTUALIZACIÓN jun 2026:** las Fases 1-3 y parte de la 4 se ejecutaron en la
> pasada única (commits `573ec4c` en adelante). Estado por ítem abajo.
>
> ✅ HECHO: PIN hasheado (migración perezosa) · sync visible en Ajustes +
> timeout de ack (fix del cuelgue offline) · 27 unit tests (Iso6346, OCR,
> totales, PinHasher) con CI bloqueante · EstimadoTotals única fuente del
> cálculo · docs (FIREBASE.md, borrado ESTIMADOS_PLAN/SUPABASE) · limpiezas
> (stateIn directo, keys en timeline, warnings) · compresión de fotos.
>
> ⏳ PENDIENTE: ver "Pendientes restantes" al final.

---

## FASE 1 — Seguridad y robustez de datos (PRIORITARIO)

### 1.1 PIN en texto plano 🔴
- **Evidencia:** `core/data/.../AuthRepositoryImpl.kt:38` — `if (row.pin != pin)`.
  El PIN se guarda tal cual en Room y en Firestore.
- **Acción:** guardar hash (SHA-256 + salt por usuario, o `bcrypt` ligero) en vez del
  PIN. Migración Room (columna `pin` → `pinHash`) + migrar usuarios existentes al
  primer login (hashear el PIN ingresado si coincide con el texto plano almacenado,
  re-guardar como hash). Coordinar con los documentos de Firestore (campo nuevo,
  mantener compatibilidad mientras todos los dispositivos actualizan).
- **Riesgo:** romper logins existentes → la migración perezosa en login lo evita.

### 1.2 Fallos de sincronización silenciosos 🔴
- **Evidencia:** `core/data/.../FirestoreService.kt` — 13 `Log.w/Log.e` sin más;
  si un `upsert*` falla, el dato queda solo en Room y nadie se entera.
- **Acción (en orden):**
  1. Verificar si la persistencia offline del SDK de Firestore está activa
     (en Android viene por defecto): si lo está, los writes se re-encolan solos
     y la deuda real es menor de lo que parece — documentarlo.
  2. Si hay huecos (p. ej. writes hechos con la app cerrada a mitad), añadir tabla
     `pending_writes` en Room (entidad, payload JSON, intentos) que se vacíe al
     arrancar el shell autenticado.
  3. Indicador de estado de sync en Ajustes (última sync OK / pendientes).

### 1.3 Writes a Firestore sin agrupar 🟡
- **Evidencia:** cada cambio (cada foto subida) dispara un `upsertEstimado` completo.
- **Acción:** debounce de ~2s en `EstimadoViewModel` para los upserts, o batch al
  guardar. Solo si 1.2.1 confirma que no lo cubre el SDK.

---

## FASE 2 — Tests (la deuda más grande)

- **Evidencia:** solo existe `app/src/test/.../ExampleUnitTest.kt` (plantilla).
  Ningún test en `core/*` ni `feature/*`. El CI corre tests "informativos".
- **Acción — empezar por la lógica pura (sin Android, barata de testear):**
  1. `Iso6346` (validación + check digit) — es el corazón del OCR.
  2. `TextRecognitionAnalyzer.correctContainerChars` y `majorityVote` (son
     `internal`/`private` en companion — exponer `internal` + `@VisibleForTesting`).
  3. `EstimadoUiState` / cálculo de totales e IVA (extraer a función pura si hace falta).
  4. DAOs con Room in-memory (`AnnouncementDao.replaceAllDiff`, `observeUnreadCount`,
     `InspectionDao.updateDigitacion`) — valida también las migraciones 11→14 con
     `MigrationTestHelper`.
  5. Cuando haya base: subir tests a obligatorios en `.github/workflows/ci.yml`
     (quitar el "informativo").

---

## FASE 3 — Mantenibilidad

### 3.1 Strings hardcodeados (0 usos de `stringResource`)
- Toda la UI tiene textos en español inline. No urge para i18n (la app es solo
  español), pero impide tests de UI estables y centralizar terminología.
- **Acción:** mover a `strings.xml` por módulo **solo al tocar cada pantalla**
  (regla de boy-scout), no como big-bang.

### 3.2 Documentación desactualizada / engañosa
- `ESTIMADOS_PLAN.md`: marcado "PENDIENTE DE IMPLEMENTACIÓN" pero ya está hecho → borrar
  o marcar como completado.
- `SUPABASE.md` + variables `SUPABASE_URL`/`SUPABASE_ANON_KEY`: el backend real es
  **Firebase**, no Supabase. Renombrar doc/variables (BuildConfig, CI secrets,
  `local.properties`) en un solo commit coordinado. Riesgo bajo pero tocar CI con cuidado.
- Añadir en `README.md` la sección de arquitectura (módulos, offline-first, roles).

### 3.3 Limpiezas menores (hacer de paso, no como tarea propia)
- `MutableStateFlow + asStateFlow()` → `stateIn` directo donde el estado es 100% reactivo
  (`UnitListViewModel`, `UnitDetailViewModel`, `SettingsViewModel`).
- `UnitTimeline.kt:47` — `forEachIndexed` sin key (animaciones erráticas al insertar).
- `ProjectionCharDetector` flood-fill sin límite de iteraciones (OOM teórico con
  iluminación pésima) — añadir tope.
- Warnings de compilación actuales: `Locale(String,String)` deprecado,
  `Icons.Outlined.Assignment` → AutoMirrored, anotaciones `@param:`.

---

## FASE 4 — Rendimiento pendiente (opcional, ya mitigado)

- **Baseline Profiles + `profileinstaller`** (~30% arranque/navegación): módulo
  `:baselineprofile` con macrobenchmark; si CI no soporta emulador, generar local y
  commitear `baseline-prof.txt`.
- **Métricas del compilador Compose** detrás de `-PcomposeMetrics` para confirmar que
  `DamageItemCard` y las cards de lista quedaron skippable.
- **Paginación del historial** en `UnitDetailScreen` (`getAllByContainerNo` sin LIMIT) —
  solo si aparecen contenedores con cientos de inspecciones.
- **Smoke test del build release** (`:app:assembleRelease` + probar APK): el minify/R8
  está activo pero nadie ha validado un release recientemente.

---

## Pendientes restantes (actualizado tras la pasada única)

1. **Tests de DAO y migraciones Room** — requieren dispositivo/emulador
   (`MigrationTestHelper`, Room in-memory). Hacer desde el PC con Android Studio.
2. **Baseline Profiles** — requiere emulador (no disponible en el contenedor
   remoto). Receta desde el PC:
   - Nuevo módulo `:baselineprofile` con plugins `androidx.baselineprofile` +
     `com.android.test`, managed device `pixel6Api34`.
   - Journey: arranque → login → anuncios → unidades → abrir estimado.
   - `./gradlew :app:generateBaselineProfile` → commitear
     `app/src/release/generated/baselineProfiles/baseline-prof.txt`.
   - `androidx.profileinstaller` ya quedó añadido en `app/`.
3. **Strings a recursos** — regla boy-scout: mover a `strings.xml` al tocar
   cada pantalla (no big-bang).
4. **Flood-fill de ProjectionCharDetector** — verificado: ya está acotado
   (stack predimensionado + visited); no requiere cambio.
5. **Paginación del historial en UnitDetail** — solo si aparecen contenedores
   con cientos de inspecciones (hoy mitigado con 2 + confirmación).
6. **Renombrar secrets SUPABASE_* → FIREBASE_*** — opcional; coordinar cambio
   simultáneo en GitHub Secrets + ci.yml + BuildConfig (documentado en FIREBASE.md).

## Decisión de arquitectura — Styles API (jun 2026)
Se intentó adoptar la Styles API de Compose (foundation `1.12.0-alpha03`,
`Style{}` + `Modifier.styleable`). **Diferida**: esa versión alpha arrastra todo
Compose a 1.12-alpha y exige `compileSdk 37`, que aún no está publicado en el
SDK manager. Re-evaluar cuando la API llegue a beta/estable sobre SDK 37 estable
y Material3 la soporte. La superficie de la API ya está estudiada
(`Style { background/shape/minHeight; disabled{} }`, `rememberUpdatedStyleState`).
