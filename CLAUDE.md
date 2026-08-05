# CheckingContainer — instrucciones para Claude

App Android offline-first para inspección de contenedores refrigerados (reefer units).
Toda la UI y la comunicación con el usuario es en **español**.

> **Al iniciar sesión, lee `CONTEXTO_PROYECTO.md`** (memoria técnica del proyecto:
> arquitectura, convenciones, gotchas) y `PLAN_DEUDA_TECNICA.md` (pendientes
> priorizados). Con eso retomas el contexto sin re-explorar el repo.

## Flujo de trabajo Git (IMPORTANTE)

El propietario alterna entre su PC (Windows) y Claude Code desde el móvil. Su PC baja
los cambios automáticamente solo de la rama **`main`**. Por eso:

- **Trabaja siempre sobre `main` y haz push directo a `main`.**
- **NO abras Pull Requests** ni crees ramas `claude/...` salvo que el usuario lo pida
  explícitamente. El objetivo es que el trabajo quede en `main` sin pasos manuales.
- Haz **commit y push a `main`** al terminar un cambio, para que el PC del usuario lo
  reciba al encender o desbloquear.
- Mensajes de commit en español, claros y concisos.

> Si el entorno remoto impide empujar a `main` directamente y obliga a una rama + PR,
> avisa al usuario y mergea el PR a `main` (o pídele que lo haga) — el trabajo NO debe
> quedarse en una rama sin integrar.

## Tamaño y responsabilidad de los archivos (RECORDAR SIEMPRE)

**La falta no es el tamaño: es mezclar responsabilidades.** Las ~300 líneas son la
señal de alarma, no la regla.

Antes de agregar una función nueva a un archivo existente:

1. **Mira cuántas líneas tiene.** Si al agregarla pasa de ~300, para y pregúntate
   qué hay dentro.
2. **Si el archivo hace UNA sola cosa** y es grande porque esa cosa es grande, se
   queda como está. Ejemplos válidos en este repo:
   - `EstimadoPdfGenerator.kt` (461) — solo dibuja el PDF sobre un canvas.
   - `EstimadoScreen.kt` (446) — solo estructura la pantalla; ni cálculo ni repos.
3. **Si mezcla responsabilidades, se parte** — sin importar el tamaño. Señales:
   - Un `@Composable` dentro de un archivo de lógica (o al revés).
   - Un ViewModel que decodifica bitmaps, parsea, o habla con el framework en vez
     de coordinar estado (fue el caso de `compressForUpload`, sacado a
     `CompresorDeFotos`).
   - Una pantalla que calcula reglas de negocio en vez de recibirlas del estado.
4. **Avísale al usuario ANTES de escribir**, con el número y el motivo:
   *"`EstimadoScreen.kt` va por 280; esta función lo pasa de 300 y además mete
   lógica de cálculo. ¿Lo parto primero?"*

Esto se ignoró durante meses y `EstimadoScreen.kt` llegó a **1.341 líneas** apilando
siete funcionalidades seguidas (fotos por ítem, aviso de cambios sin guardar,
mediciones BLE, catálogo de clientes, tipo de equipo, orden de trabajo, diagnóstico).
Costó una pasada entera de refactor.

No hay linter que lo verifique: **depende de que lo recuerdes en cada cambio.**
