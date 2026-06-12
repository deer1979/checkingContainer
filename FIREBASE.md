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
| `users` | `{id}` | nombre, nick, **pin (hash v1, ya no texto plano)**, rol, activo |
| `reefer_units` | `{containerNo}` | datos del equipo frigorífico |
| `reefer_units/{containerNo}/inspections` | `{id}` | inspecciones + campos de digitación |
| `estimados` | `{id}` | estimado con ítems de daño (JSON) y costos |
| `announcements` | `{id}` | anuncios con adjuntos (URLs de Storage) |

## Comportamiento offline-first

- Todo se guarda primero en Room; luego se hace upsert a Firestore.
- Los writes tienen **timeout de ack de 10s**: sin conexión, el cambio queda en
  la caché local del SDK de Firestore y se reenvía solo al volver la red
  (estado visible en **Ajustes → Sincronización**).
- Cambios de digitación llegan por listener `collectionGroup` solo mientras la
  app autenticada está en primer plano.
- Las fotos se comprimen a JPEG 80 / máx 1600px antes de subir a Storage.
