# Conexión a Supabase

La app sincroniza datos con Supabase automáticamente cuando se configuran las credenciales.

## Cómo configurar las credenciales

### Desarrollo local (Android Studio)

1. Abrí el **Supabase Dashboard** → **Project Settings** → **API**
2. Copiá:
   - **Project URL** → ej: `https://xxxxxxxxxxxx.supabase.co`
   - **anon public** key (bajo *Project API keys*)
3. Editá (o creá) el archivo `local.properties` en la raíz del proyecto:

```properties
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

> ⚠️ `local.properties` está en `.gitignore` — nunca se sube a GitHub.

### CI / GitHub Actions

1. Ir a **GitHub → tu repositorio → Settings → Secrets and variables → Actions**
2. Agregar dos Repository secrets:
   - `SUPABASE_URL` → el Project URL
   - `SUPABASE_ANON_KEY` → la anon key

El workflow `.github/workflows/ci.yml` ya los inyecta automáticamente en cada build.

---

## Tablas requeridas en Supabase

Si la app no puede guardar datos, las tablas pueden estar faltando o tener columnas diferentes. Chequeá que existan con estas columnas:

### `users`
| Columna      | Tipo      |
|-------------|-----------|
| id          | bigint PK |
| first_name  | text      |
| last_name   | text      |
| nick        | text (unique) |
| pin         | text      |
| job_title   | text      |
| role        | text      |
| company     | text      |
| location    | text      |
| is_active   | boolean   |
| local_id    | bigint    |

### `reefer_units`
| Columna         | Tipo      |
|----------------|-----------|
| id             | bigint PK |
| container_no   | text      |
| manufacturer   | text      |
| unit_model     | text      |
| unit_model_no  | text      |
| unit_serial_no | text      |
| year_of_built  | text      |
| created_at_ms  | bigint    |
| status         | text      |
| pti_instruction| text      |
| unit_type      | text      |
| deployed_as    | text      |
| technician_id  | bigint    |
| technician_name| text      |
| observations   | text      |
| local_id       | bigint    |

### `announcements`
| Columna       | Tipo   |
|--------------|--------|
| id           | text PK |
| title        | text   |
| summary      | text   |
| body         | text   |
| author_name  | text   |
| published_at_ms | bigint |

---

## Comportamiento sin credenciales

Si `SUPABASE_URL` o `SUPABASE_ANON_KEY` están vacíos, la app funciona en **modo local** (solo Room/SQLite). Los datos no se sincronizan con Supabase pero todo lo demás funciona normalmente.

## Comportamiento con credenciales

- **Al arrancar la app**: intenta traer datos de Supabase y los guarda en Room
- **Al crear/editar un usuario**: se guarda en Room primero, luego se sube a Supabase en background
- **Al crear/editar una unidad frigorífica**: ídem
- **Al publicar un anuncio**: ídem
- **Si falla la conexión**: la operación queda en Room; en el próximo arrange se reintenta
