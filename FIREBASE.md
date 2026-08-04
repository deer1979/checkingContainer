# Conexión a Firebase (Firestore + Storage)

El backend real de la app es **Firebase** (Cloud Firestore para datos y Cloud
Storage para fotos/adjuntos), configurado vía `app/google-services.json`.

> ⚠️ **Nombre legado:** por historia del proyecto, las variables de entorno y
> secrets se llaman `SUPABASE_URL` / `SUPABASE_ANON_KEY` aunque ya no se usa
> Supabase. NO renombrarlas sin coordinar: el CI y `BuildConfig` dependen de
> esos nombres. Si están vacías, la app arranca en **modo local** (solo
> Room/SQLite, sin sincronización).

## Configuración

### Desarrollo local
- `app/google-services.json` debe existir (consola de Firebase → Project
  Settings → tus apps → descargar).
- `local.properties` puede definir `SUPABASE_URL`/`SUPABASE_ANON_KEY` (legado)
  para activar el modo conectado.

### CI / GitHub Actions
- Secrets del repositorio: `SUPABASE_URL` y `SUPABASE_ANON_KEY` (nombres
  legados). El workflow `.github/workflows/ci.yml` los inyecta en cada build.

## Colecciones en Firestore

| Colección | Documento | Contenido |
|---|---|---|
| `users` | `{id}` | nombre, nick, PIN PBKDF2 v2, rol, activo |
| `reefer_units` | `{containerNo}` | datos del equipo frigorífico |
| `reefer_units/{containerNo}/inspections` | `{id}` | inspecciones + campos de digitación |
| `estimados` | `{id}` | estimado con ítems de daño (JSON) y costos |
| `announcements` | `{id}` | anuncios con adjuntos (URLs de Storage) |

Los valores `pin` antiguos en texto plano o formato `v1` se aceptan solo para
compatibilidad. Después de un login correcto se sustituyen automáticamente por
PBKDF2-HMAC-SHA256 `v2` en Room y Firestore.

## Capa de identidad: `FirebaseSession`

La aplicación ya no depende directamente de una clase de autenticación
anónima. `FirebaseSession`, en `core:network`, concentra el estado de Firebase:

- conserva un UID confiable existente y nunca lo reemplaza por uno anónimo;
- mantiene temporalmente `signInAnonymously()` para que los APK instalados y el
  funcionamiento offline sigan operativos durante la migración;
- acepta `signInWithCustomToken()` para elevar la sesión a un UID estable
  emitido por un backend confiable;
- no persiste ni registra custom tokens;
- permite consultar si la identidad activa es anónima o confiable.

Esta capa es solo la primera fase de la incidencia #8. Todavía falta el servicio
servidor que valide `usuario + PIN`, emita el custom token y agregue claims de
rol, empresa y alcance operativo.

## Seguridad actual: autenticación anónima transitoria

Mientras el backend de intercambio no esté desplegado, `FirebaseSession` crea
una **sesión anónima compatible** al arrancar y la reintenta desde
`BootstrapRepositoryImpl` cuando hace falta. Esta sesión mantiene operativos los
APK actuales, pero **no identifica al usuario interno ni su rol**.

La configuración actualmente documentada es transitoria:

```
// Firestore
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}

// Storage
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

> **No considerar estas reglas seguras para producción multiusuario.**
> `request.auth != null` solo confirma que existe una sesión Firebase. Con
> autenticación anónima no demuestra que la persona sea técnico, administrador
> ni miembro de una empresa concreta. Tampoco protege `users`, roles, PIN,
> inspecciones, estimados o archivos frente a otra sesión anónima válida.

La migración obligatoria está registrada en la incidencia **#8: reemplazar
autenticación anónima y cerrar reglas Firebase**. El diseño mantiene la pantalla
`usuario + PIN`, pero debe obtener una identidad Firebase confiable por usuario,
aplicar roles mediante custom claims, versionar reglas y cubrirlas con Firebase
Emulator Suite.

Requisitos actuales en la consola:
1. **Authentication → Sign-in method → Anonymous → habilitar**, mientras los
   APK existentes dependan de este flujo transitorio.
2. No endurecer las reglas directamente en producción sin desplegar antes el
   intercambio de custom token y una migración compatible. Los APK antiguos
   quedarían bloqueados.

## Secuencia de migración prevista

1. Incorporar `FirebaseSession` sin cambiar el comportamiento de campo.
2. Crear un backend confiable que valide `nick + PIN` y emita custom tokens.
3. Elevar la sesión después del login local, manteniendo acceso offline.
4. Separar credenciales de perfiles operativos y dejar de listar hashes.
5. Versionar y probar reglas por UID, rol, empresa y ruta.
6. Desplegar el APK compatible antes de bloquear sesiones anónimas.
7. Retirar el fallback anónimo cuando ya no existan APK antiguos activos.

## Crashlytics (reporte de fallos)

La app envía los cierres inesperados a **Firebase Crashlytics**. Requisito en
la consola (una sola vez): **Compilación → Crashlytics → Habilitar**. Tras el
primer crash (o prueba) los reportes aparecen ahí con línea y modelo de equipo.
Activo también en el APK debug (que es el que se usa en campo).

## Comportamiento offline-first

- Todo se guarda primero en Room; luego se hace upsert a Firestore.
- Los writes tienen **timeout de ack de 10s**: sin conexión, el cambio queda en
  la caché local del SDK de Firestore y se reenvía al volver la red
  (estado visible en **Ajustes → Sincronización**).
- El bootstrap inicial usa un marcador persistente. Si la primera descarga
  ocurre sin conexión o Firestore no devuelve usuarios, queda pendiente y se
  reintenta en el siguiente arranque de login.
- Cambios de digitación llegan por listener `collectionGroup` mientras la app
  autenticada está en primer plano.
- Las fotos se comprimen a JPEG 80 / máx. 1600 px antes de subir a Storage.
