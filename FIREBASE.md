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

## Seguridad actual: autenticación anónima transitoria

La app obtiene una **sesión anónima de Firebase** al arrancar
(`AnonymousAuth` en `core:network`; reintento en cada login vía
`BootstrapRepositoryImpl`). Esta sesión mantiene operativos los APK actuales,
pero **no identifica al usuario interno ni su rol**.

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
autenticación anónima y cerrar reglas Firebase**. El diseño acordado debe
mantener la pantalla `usuario + PIN`, pero obtener una identidad Firebase
confiable por usuario, aplicar roles mediante custom claims o un servicio
seguro, versionar las reglas y cubrirlas con Firebase Emulator Suite.

Requisitos actuales en la consola:
1. **Authentication → Sign-in method → Anonymous → habilitar**, mientras los
   APK existentes dependan de este flujo transitorio.
2. No endurecer las reglas directamente en producción sin desplegar antes la
   identidad por usuario y una migración compatible. Los APK antiguos quedarían
   bloqueados.

La sesión anónima persiste entre arranques. El primer inicio conectado necesita
red para crearla.

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
