# Plan de implementación — Módulo Estimados v2

> Estado: **PENDIENTE DE IMPLEMENTACIÓN**
> Sesión de planificación: 2026-06-10
> Implementar de una sola pasada cuando haya cuota completa.

---

## Contexto

El módulo `feature/units/Estimado*` ya existe (Fase 1) pero necesita rediseño completo.
El código actual tiene un solo campo de daño + fotos en grid. El plan nuevo tiene
ítems de daño individuales, cada uno con foto antes/después lado a lado, valores por ítem,
y flujo de reparación ítem por ítem.

---

## Modelo de datos nuevo

### `DamageItem` — nuevo en `core/model/Estimado.kt`
```kotlin
data class DamageItem(
    val id: String = UUID.randomUUID().toString(),
    val damageDescription: String = "",
    val damagePhoto: String? = null,       // URL Firebase Storage — foto ANTES
    val repairAction: String = "",
    val repairPhoto: String? = null,       // URL Firebase Storage — foto DESPUÉS
    val status: DamageItemStatus = DamageItemStatus.PENDIENTE,
    val laborCost: Double? = null,         // Mano de obra en USD
    val materialCost: Double? = null,      // Repuesto/material en USD
)

enum class DamageItemStatus { PENDIENTE, REPARADO }
```

### `Estimado` — reemplazar campos sueltos de daño/reparación
```kotlin
data class Estimado(
    val id: Long = 0,
    val inspectionId: Long,
    // Datos del contenedor (copiados de ReeferUnit al crear)
    val containerNo: String,
    val manufacturer: String = "",
    val unitModel: String = "",
    val unitModelNo: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val unitType: String = "",
    // Datos del estimado
    val clientName: String = "",
    val location: String = "",
    val technicianId: Long = 0,
    val technicianName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,          // auto: cuando se toca "Reparar" en el primer ítem
    val closedAt: Long? = null,            // auto: cuando se cierra el estimado
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    // Ítems de daño
    val damages: List<DamageItem> = emptyList(),
    // Configuración
    val hasIva: Boolean = false,           // IVA 12% Ecuador
    val reportUrl: String? = null,
)

enum class EstimadoStatus { ABIERTO, CERRADO }
// Nota: EstimadoFase y REPARADO ya no se usan — el estado es por DamageItem
```

---

## Base de datos — Migración 13 → 14

### `EstimadoEntity` — reemplazar columnas sueltas por `damages TEXT`
```
damages TEXT DEFAULT '[]'    -- JSON array de DamageItem
manufacturer TEXT DEFAULT ''
unitModel TEXT DEFAULT ''
unitModelNo TEXT DEFAULT ''
unitSerialNo TEXT DEFAULT ''
yearOfBuilt TEXT DEFAULT ''
unitType TEXT DEFAULT ''
approvedAt INTEGER            -- nullable
hasIva INTEGER NOT NULL DEFAULT 0
```

Columnas que se eliminan: `damageDescription`, `damagePhotos`, `repairDescription`, `repairPhotos`

### `Migration13to14.kt`
```sql
ALTER TABLE estimados ADD COLUMN damages TEXT NOT NULL DEFAULT '[]';
ALTER TABLE estimados ADD COLUMN manufacturer TEXT NOT NULL DEFAULT '';
ALTER TABLE estimados ADD COLUMN unitModel TEXT NOT NULL DEFAULT '';
ALTER TABLE estimados ADD COLUMN unitModelNo TEXT NOT NULL DEFAULT '';
ALTER TABLE estimados ADD COLUMN unitSerialNo TEXT NOT NULL DEFAULT '';
ALTER TABLE estimados ADD COLUMN yearOfBuilt TEXT NOT NULL DEFAULT '';
ALTER TABLE estimados ADD COLUMN unitType TEXT NOT NULL DEFAULT '';
ALTER TABLE estimados ADD COLUMN approvedAt INTEGER;
ALTER TABLE estimados ADD COLUMN hasIva INTEGER NOT NULL DEFAULT 0;
```
> Las columnas viejas (damageDescription, etc.) se dejan — Room las ignora si no están en la Entity.

### `AppDatabase` → versión 14, agregar MIGRATION_13_14

---

## Serialización de DamageItem

En `EstimadoEntity`, `damages` se almacena como JSON array de objetos:
```json
[
  {
    "id": "uuid",
    "damageDescription": "Filtro tapado",
    "damagePhoto": "https://...",
    "repairAction": "Se reemplazó filtro",
    "repairPhoto": "https://...",
    "status": "REPARADO",
    "laborCost": 15.0,
    "materialCost": 8.5
  }
]
```
Usar `org.json.JSONArray` + `JSONObject` (sin librerías extra, disponible en Android).

---

## Firebase Storage — rutas

```
estimados/{inspectionId}/items/{itemId}/dano.jpg
estimados/{inspectionId}/items/{itemId}/reparacion.jpg
```
Una sola foto por fase por ítem (no múltiples). Sobrescribir si se vuelve a subir.

---

## EstimadosRepository — cambios

### Nuevo método en interfaz (`core/domain`):
```kotlin
suspend fun uploadItemPhoto(inspectionId: Long, itemId: String, isDano: Boolean, bytes: ByteArray): String
suspend fun deletePhoto(url: String)
suspend fun observeAllOpen(): Flow<List<Estimado>>   // para la lista con badge
suspend fun countOpen(): Flow<Int>                   // para el badge del nav
```

---

## Pantallas — mapa completo

```
Nav inferior: [Anuncios]  [Unidades]  [Estimados 🔴N]  [Ajustes]
                                           ↓
                               EstimadosListScreen
                               (lista de ABIERTOS)
                                    [Tab: Cerrados]
                                           ↓
                               EstimadoScreen (scrollable largo)
```

---

## EstimadoScreen — layout

```
TopAppBar: "Estimado — BMOU901290-9"  [Valores y PDF]

CLIENTE
  Nombre del cliente: [campo]
  Localidad: [campo]
  Fecha: 10/06/2026 (auto)

EQUIPO
  No. Contenedor: BMOU901290-9
  No. Serie: SN-XXXXX
  Fabricante: Carrier    Modelo: Vector 1550
  Año: 2018    Tipo: Reefer

DAÑOS ENCONTRADOS
  ┌─ Ítem 1 ──────────────────────────────────────────┐
  │ [texto daño]                                       │
  │ [foto antes 1:1 redondeada]  [foto después 1:1]   │
  │ (si reparado)                                      │
  │ [acción de reparación - texto]                     │
  │                         [🔧 Reparar ítem]          │
  └────────────────────────────────────────────────────┘
  ┌─ Ítem 2 ──────────────────────────────────────────┐
  │ ...                              ✓ REPARADO        │
  └────────────────────────────────────────────────────┘

[+ Agregar daño]

[Guardar estimado]   (siempre visible hasta CERRADO)
```

### Al tocar "Agregar daño" → ModalBottomSheet:
```
AGREGAR DAÑO
  ¿Qué se encontró?
  [campo texto multiline]

  Foto del daño
  [📷 Cámara]    [🖼 Galería]
  [miniatura si hay foto]

  [Cancelar]    [Guardar daño]
```

### Al tocar "🔧 Reparar ítem" → ModalBottomSheet:
```
REPARAR — ÍTEM 1
  Referencia: "Filtro tapado..." (bloqueado)

  ¿Qué se hizo?
  [campo texto multiline]

  Foto de la reparación
  [📷 Cámara]    [🖼 Galería]
  [miniatura si hay foto]

  [Cancelar]    [Guardar reparación]
```

---

## ValoresScreen — layout

Ruta: `estimados/{inspectionId}/valores`

```
TopAppBar: "Valores — BMOU901290-9"

ÍTEM 1  Filtro de línea tapado              [✏️]
  Mano de obra:  $ 0.00
  Material:      $ 0.00

ÍTEM 2  Compresor ruidoso                   [✏️]
  Mano de obra:  $ 0.00
  Material:      $ 0.00

─────────────────────────────────────────
Subtotal mano de obra:     $  40.00
Subtotal materiales:       $  23.50
IVA 12%:  [ Sí / No● ]    $   0.00
─────────────────────────────────────────
TOTAL:                     $  63.50

[📄 PDF para cliente]   [📋 Reporte final]
```

### Al tocar ✏️ → ModalBottomSheet:
```
ÍTEM 1 — Filtro de línea tapado
  Mano de obra    [$ ___________]
  Costo material  [$ ___________]
  [Cancelar]  [Guardar]
```

---

## EstimadosListScreen — layout

```
TopAppBar: "Estimados"   [Tab: Abiertos | Cerrados]

Tab ABIERTOS:
  ┌──────────────────────────────────────────────────┐
  │ BMOU901290-9    Frigonorte S.A.         >        │
  │ Puerto Bolívar · 10/06/2026   🟡 ABIERTO         │
  └──────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────┐
  │ TEMU1234567     Sin nombre              >        │
  │ Guayaquil · 09/06/2026        🟡 ABIERTO         │
  └──────────────────────────────────────────────────┘

Tab CERRADOS:
  (misma lista, items con status CERRADO, sin badge)
```

---

## Navegación — cambios en AuthenticatedShell

Agregar destino "Estimados" al nav inferior con `BadgedBox`:
```kotlin
// TopLevelDestination: agregar ESTIMADOS
ESTIMADOS(
    route = ESTIMADOS_LIST_ROUTE,
    label = "Estimados",
    icon = Icons.Outlined.Assignment,
    selectedIcon = Icons.Filled.Assignment,
)
```
Badge = `countOpen()` Flow, se muestra si > 0.

---

## PDF — dos versiones

### PDF Estimado (para enviar al cliente antes de reparar)
- Header: logo empresa + "ESTIMADO DE REPARACIÓN #N" + fecha
- Bloque cliente: nombre, localidad, elaborado por, fecha
- Bloque equipo: todos los campos técnicos
- Por ítem: descripción daño + foto daño (ancho completo o 50%)
- Tabla de valores: ítem | mano de obra | material
- Totales: subtotal + IVA + total
- Pie: firma técnico / aprobado cliente (fecha)

### PDF Reporte final (después de reparar)
- Todo lo anterior, más:
- Por ítem: foto ANTES (izquierda) | foto DESPUÉS (derecha) lado a lado
- Acción realizada debajo de cada ítem
- Fecha de inicio reparación (approvedAt) + fecha cierre (closedAt)

### Tecnología PDF
HTML inline → WebView fuera de pantalla → `createPrintDocumentAdapter()` → ByteArray
Fotos en base64 inline. Sin librerías externas.

---

## Datos del contenedor en el Estimado

Al crear un estimado desde UnitEntry (status EST):
- Cargar el `ReeferUnit` por `containerNo`
- Copiar: `manufacturer`, `unitModel`, `unitModelNo`, `unitSerialNo`, `yearOfBuilt`, `unitType`
- Estos datos se guardan EN el Estimado (snapshot) para que el PDF siempre tenga los datos correctos aunque el ReeferUnit cambie después

En `EstimadoViewModel.init`:
```kotlin
val unit = reeferUnitRepo.findByContainerNo(inspection.containerNo)
// copiar campos al state
```

---

## Archivos a crear/modificar

### Nuevos
- `core/database/migrations/Migration13to14.kt`
- `feature/units/EstimadosListScreen.kt`
- `feature/units/ValoresScreen.kt`
- `feature/units/EstimadoPdfGenerator.kt`

### Modificados
- `core/model/Estimado.kt` — DamageItem, campos técnicos, approvedAt, hasIva
- `core/database/entity/EstimadoEntity.kt` — nueva serialización
- `core/database/AppDatabase.kt` — versión 14
- `core/database/di/DatabaseModule.kt` — MIGRATION_13_14
- `core/domain/EstimadosRepository.kt` — observeAllOpen, countOpen
- `core/data/EstimadosRepositoryImpl.kt` — implementar nuevos métodos
- `core/data/FirestoreService.kt` — mapeo nuevo modelo
- `feature/units/EstimadoUiState.kt` — estado por DamageItem
- `feature/units/EstimadoViewModel.kt` — lógica nueva
- `feature/units/EstimadoScreen.kt` — rediseño completo
- `feature/units/navigation/UnitsNavigation.kt` — rutas ValoresScreen, EstimadosListScreen
- `app/.../TopLevelDestination.kt` — agregar ESTIMADOS
- `app/.../AuthenticatedShell.kt` — badge en nav

---

## Reglas de negocio

1. `approvedAt` se auto-llena al guardar la primera reparación de cualquier ítem
2. El botón "Reparar ítem" aparece solo cuando el estimado está guardado (id != 0)
3. Un ítem REPARADO queda bloqueado — no se puede editar
4. Todos los ítems REPARADO → habilita "Cerrar estimado" → status = CERRADO
5. Todo es editable (incluso valores) hasta que el estimado esté CERRADO
6. El PDF se puede generar y regenerar N veces
7. Moneda: USD ($) fija. IVA: 12% (Ecuador). Toggle por estimado.
8. Una sola foto por fase por ítem (no grid de múltiples fotos)
9. Foto del daño: izquierda. Foto de reparación: derecha. Lado a lado.
10. Los botones "Reparar" NO aparecen en el PDF del cliente

---

## UX para usuario no técnico (socio mayor)

- Todos los botones llevan texto + ícono (nunca solo ícono)
- Touch targets mínimo 48dp
- Teclado numérico para campos de dinero
- Confirmación antes de borrar cualquier cosa
- Mensajes claros: "Daño guardado", "Reparación registrada", "Estimado guardado"
- Colores de estado: amarillo = abierto, verde = reparado/cerrado
