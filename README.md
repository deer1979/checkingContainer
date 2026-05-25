# Testo3 — Modern Android Starter

Proyecto Android construido apuntando al **stack más alto posible** para validar
compatibilidad y preparación a las próximas tecnologías de Android (foldables,
pantallas grandes, HDR, predictive back, 16KB page size, etc.).

## Stack

| Componente            | Versión / objetivo                        |
|-----------------------|--------------------------------------------|
| Android Gradle Plugin | 8.9.1                                     |
| Gradle                | 8.14.3                                    |
| Kotlin                | 2.1.10 (compilador K2)                    |
| JDK toolchain         | 21                                        |
| compileSdk            | 36 (Android 16 "Baklava")                 |
| targetSdk             | 36                                        |
| minSdk                | 26 (Android 8.0)                          |
| UI                    | Jetpack Compose (Material 3)              |
| Compose BOM           | 2025.04.01                                |
| Adaptive layouts      | androidx.compose.material3.adaptive       |

## Características modernas habilitadas

- ✅ Edge-to-edge (`enableEdgeToEdge`) y manejo de insets sin barras.
- ✅ Predictive Back Gesture (`enableOnBackInvokedCallback="true"`).
- ✅ Splash Screen API.
- ✅ Material 3 con **Dynamic Color** (Android 12+).
- ✅ `WindowSizeClass` para foldables y pantallas grandes.
- ✅ Alineación a páginas de **16 KB** (AGP ≥ 8.5 lo aplica por defecto).
- ✅ Backup rules y data extraction rules (Android 12+).
- ✅ Java/Kotlin toolchain en 21 (mismo bytecode que AGP requiere).

## Cómo abrir

1. Abre Android Studio (Narwhal | 2025.1.1 o superior recomendado).
2. **File → Open** → selecciona la raíz del repo.
3. Deja que Gradle sincronice (descargará AGP, Kotlin y el SDK 36 si falta).
4. **Run ▶** sobre el módulo `app`.

## Compilar por línea de comandos

```bash
./gradlew assembleDebug          # APK debug
./gradlew installDebug           # instalar en dispositivo conectado
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented tests (necesita device/emulator)
./gradlew lint                   # análisis estático
```

## Estructura

```
.
├── app/
│   ├── src/main/java/com/example/myapplication/    # código Kotlin
│   ├── src/main/res/                                # recursos
│   └── build.gradle.kts
├── gradle/libs.versions.toml                        # version catalog
├── settings.gradle.kts
└── build.gradle.kts
```
