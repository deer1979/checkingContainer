# checkingContainer

Aplicación Android corporativa de gestión de personal y operaciones, construida con arquitectura multi-módulo moderna.

## Stack

| Componente             | Versión                       |
|------------------------|-------------------------------|
| Android Gradle Plugin  | 9.2.1                         |
| Gradle                 | 9.4.1                         |
| Kotlin                 | 2.2.20 (K2)                   |
| JDK                    | 21                            |
| compileSdk / targetSdk | 36 (Android 16)               |
| minSdk                 | 26 (Android 8.0)              |
| Jetpack Compose BOM    | 2026.05.01 (Material 3 1.4.0) |
| Hilt                   | 2.59.2                        |
| Room                   | 2.7.1                         |
| Navigation Compose     | 2.9.0                         |

## Funcionalidades

- Autenticación con email generado + PIN de 6 dígitos
- Roles: SuperAdmin, Admin, Editor, Viewer
- Gestión de usuarios (alta, edición, activación)
- Tareas y anuncios por rol
- Panel de administración
- Seed automático de SuperAdmin al primer arranque (`sadmin@checkingcontainer.app` / `000000`)

## Arquitectura

```
checkingContainer/
├── app/                        # módulo principal, navegación raíz
├── build-logic/convention/     # plugins Gradle personalizados
├── core/
│   ├── model/                  # entidades de dominio
│   ├── domain/                 # casos de uso
│   ├── data/                   # repositorios
│   ├── database/               # Room + DAOs
│   ├── designsystem/           # tema, colores, tipografía
│   └── common/                 # utilidades compartidas
└── feature/
    ├── splash/
    ├── login/
    ├── tasks/
    ├── announcements/
    ├── users/
    ├── units/
    ├── admin/
    └── settings/
```

## Abrir en Android Studio

1. Android Studio Panda 4 (2025.3.4) o superior
2. **File → Open** → selecciona la raíz del repo
3. Gradle sincroniza automáticamente (requiere JDK 21)
4. **Run ▶** sobre el módulo `app`

## Compilar por línea de comandos

```bash
# Requiere JAVA_HOME apuntando a JDK 21
./gradlew :app:assembleDebug        # APK debug
./gradlew :app:compileDebugKotlin   # solo compilación Kotlin
./gradlew test                      # unit tests
./gradlew lint                      # análisis estático
```

En Windows (PowerShell):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio2\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:assembleDebug --no-daemon
```

## CI/CD

GitHub Actions compila y publica el APK debug automáticamente en cada push a `main`.
El APK queda disponible en la sección **Releases** del repositorio bajo el tag `latest-debug`.