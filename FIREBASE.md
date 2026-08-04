# Conexión a Firebase (Firestore + Storage)

El backend real de la app es **Firebase**: Cloud Firestore para datos, Cloud
Storage para fotos y adjuntos, Authentication para la identidad técnica,
App Check para atestación del APK y Functions para el intercambio seguro de
credenciales. La app Android se configura con `app/google-services.json`.

> ⚠️ **Nombre legado:** por historia del proyecto, las variables y secrets se
> llaman `SUPABASE_URL` / `SUPABASE_ANON_KEY` aunque Supabase ya no se utiliza.
> No deben renombrarse sin coordinar el CI y `BuildConfig`. Si están vacías, la
> app conserva su modo local con Room/SQLite.

## Configuración de desarrollo y CI

### Desarrollo local

- `app/google-services.json` debe corresponder al proyecto Firebase de prueba.
- `local.properties` puede definir `SUPABASE_URL` y `SUPABASE_ANON_KEY` con sus
  nombres legados para activar el modo conectado.

### GitHub Actions

El workflow `.github/workflows/ci.yml` valida dos bloques independientes:

1. Android: pruebas, lint Debug/Release, APK Debug, APK Release sin firmar y R8.
2. Functions: Node 22, instalación reproducible con `npm ci`, compilación
   TypeScript y pruebas del intercambio de identidad.

## Colecciones actuales

| Colección | Documento | Contenido |
|---|---|---|
| `users` | `{id}` | nombre, nick, PIN PBKDF2 v2, rol, empresa, ubicación y estado |
| `reefer_units` | `{containerNo}` | datos del equipo frigorífico |
| `reefer_units/{containerNo}/inspections` | `{id}` | inspecciones y digitación |
| `estimados` | `{id}` | estimado, daños, fotos y costos |
| `announcements` | `{id}` | anuncios y URLs de Storage |
| `_auth_attempts` | hash de sesión/nick | contador temporal de intentos fallidos |

Los PIN antiguos en texto plano o formato `v1` se aceptan únicamente para
compatibilidad. Después de un login correcto se sustituyen por
PBKDF2-HMAC-SHA256 `v2` en Room y Firestore.

## Capa Android de identidad: `FirebaseSession`

`FirebaseSession`, en `core:network`, concentra el estado de Firebase:

- mantiene temporalmente `signInAnonymously()` para que APK antiguos y el modo
  offline continúen operativos durante la migración;
- acepta `signInWithCustomToken()` para elevar la sesión a un UID estable;
- no persiste ni registra custom tokens;
- distingue identidad anónima de identidad confiable;
- elimina UID y claims confiables al arrancar el proceso y al cerrar sesión,
  evitando que otro usuario herede el rol anterior.

## Servicio de identidad: `exchangePinForToken`

El módulo `functions/` contiene una callable de Functions de segunda generación
que todavía **no debe desplegarse en producción** hasta completar App Check en
la consola.

La función:

- exige una sesión Firebase previa y un token App Check válido;
- normaliza `nick` y exige un PIN de seis dígitos;
- verifica hashes PBKDF2 `v2`, SHA-256 con salt `v1` y valores legados;
- rechaza usuarios inexistentes, duplicados o desactivados;
- limita a cinco fallos durante una ventana de quince minutos;
- emite un UID estable `cc-user-{id}`;
- añade claims mínimos: `appUserId`, `role`, `company`, `location` y `active`;
- devuelve únicamente custom token, ID de usuario y rol;
- nunca devuelve ni registra el hash del PIN.

## App Check con Play Integrity

El APK instala `PlayIntegrityAppCheckProviderFactory` antes de utilizar de forma
explícita los demás servicios Firebase. En esta fase el cliente ya puede enviar
tokens, pero el enforcement general permanece desactivado para no bloquear los
dispositivos existentes.

### Certificado del APK Debug de campo

El CI usa el keystore Debug versionado del proyecto. La huella SHA-256 validada
es:

```text
38:2B:31:D8:1E:31:07:EF:09:36:76:E1:DF:45:33:F4:A1:7D:16:A3:66:98:9F:C0:74:39:6A:E0:50:DD:85:6F
```

Debe registrarse en la app Android `com.checkingcontainer` dentro de Firebase
App Check. La firma Release se registrará cuando exista un keystore de
producción; el APK Release actual del CI es intencionalmente **sin firmar**.

### Configuración para distribución fuera de Google Play

El APK Debug de campo se distribuye directamente. Para esta fase se usará Play
Integrity, no el proveedor Debug compartido. En la configuración avanzada de
App Check deben aceptarse instalaciones externas verificadas sin exigir que la
app sea reconocida o licenciada por Google Play, manteniendo como mínimo la
integridad del dispositivo.

### Orden de activación

1. Registrar la app Android y la huella SHA-256 anterior en App Check.
2. Configurar Play Integrity para la distribución externa de campo.
3. Instalar un APK de la PR en dispositivos reales y observar métricas.
4. Configurar TTL de Firestore para `_auth_attempts.expiresAt`.
5. Desplegar `exchangePinForToken` primero en un proyecto de desarrollo.
6. Integrar el cliente Android de Functions después del login local.
7. Validar elevación de UID y claims en dispositivos de campo.
8. Versionar y probar reglas con Firebase Emulator Suite.
9. Distribuir el APK compatible a todos los dispositivos.
10. Habilitar enforcement gradualmente y retirar al final el fallback anónimo.

No se debe activar enforcement para Firestore, Storage o Authentication antes
de completar la secuencia. La callable ya declara `enforceAppCheck: true`, por
lo que tampoco debe desplegarse hasta registrar y validar el APK.

## Reglas actuales: transición, no seguridad final

La configuración histórica permite acceso global a cualquier sesión Firebase:

```text
allow read, write: if request.auth != null;
```

Esto no representa autorización por usuario, rol o empresa. Una sesión anónima
válida no demuestra que la persona sea técnico, administrador o miembro de la
organización. Las reglas finales deben usar UID y claims confiables y deben
impedir que clientes móviles lean hashes o modifiquen roles.

La migración completa continúa en la incidencia #8. Hasta que las reglas nuevas
estén probadas y todos los dispositivos tengan un APK compatible, no se deben
cerrar directamente las reglas de producción.

## Comportamiento offline-first

- Todo se guarda primero en Room y después se sincroniza con Firestore.
- Los writes tienen timeout de confirmación; sin red permanecen en la caché
  local y se reenvían al recuperar conexión.
- El bootstrap inicial usa un marcador persistente y se reintenta si la primera
  descarga ocurre sin red o no devuelve usuarios.
- Los cambios de digitación llegan mediante listener mientras la app está en
  primer plano.
- Las imágenes se comprimen antes de subir y Coil conserva caché local.
- La elevación a identidad confiable será una mejora online posterior al login;
  un fallo del backend no debe eliminar la capacidad de trabajo local.

## Crashlytics

Crashlytics está activo también en Debug, porque ese APK se usa en campo. Los
cierres inesperados se reportan con versión, dispositivo y línea cuando el
servicio está habilitado en la consola Firebase.
