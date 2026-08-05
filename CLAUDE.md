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

## Tamaño de archivos (RECORDAR SIEMPRE)

**Límite: ~300 líneas por archivo Kotlin.**

Antes de agregar una función nueva a un archivo existente, **mira cuántas líneas
tiene**. Si al agregarla se acerca o pasa el límite:

1. **Avísale al usuario ANTES de escribir**, con el número concreto:
   *"`EstimadoScreen.kt` va por 280 líneas; esta función lo pasa de 300. ¿Lo parto
   primero?"*
2. Parte el archivo primero y **después** agrega la función.

Esto se ignoró durante meses y `EstimadoScreen.kt` llegó a **1.341 líneas** apilando
siete funcionalidades seguidas (fotos por ítem, aviso de cambios sin guardar,
mediciones BLE, catálogo de clientes, tipo de equipo, orden de trabajo, diagnóstico).
Es deuda que costó una pasada entera de refactor.

No hay linter que lo verifique: **depende de que lo recuerdes en cada cambio.**
