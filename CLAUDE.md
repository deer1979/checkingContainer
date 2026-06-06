# CheckingContainer — instrucciones para Claude

App Android offline-first para inspección de contenedores refrigerados (reefer units).
Toda la UI y la comunicación con el usuario es en **español**.

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
