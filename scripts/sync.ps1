<#
.SYNOPSIS
  Sincroniza el repo CheckingContainer con GitHub.

.DESCRIPTION
  Sin argumentos  -> BAJA lo remoto (fetch + pull --rebase --autostash).
                     Esto es lo que corre la tarea programada al encender el PC.
  -Save "mensaje" -> SUBE tu trabajo (add -A + commit + push). Úsalo al terminar.

.EXAMPLE
  .\scripts\sync.ps1
  .\scripts\sync.ps1 -Save "feat: lo que hice hoy"
#>
param(
    [string]$Save
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
$log   = Join-Path $repo '.sync.log'
function Note($msg) {
    "$stamp  $msg" | Tee-Object -FilePath $log -Append
}

try {
    if ($Save) {
        Note "SUBIENDO cambios..."
        git add -A
        # Si no hay nada que commitear, no es un error.
        $pending = git status --porcelain
        if ($pending) {
            git commit -m $Save
            Note "Commit creado: $Save"
        } else {
            Note "No habia cambios sin commitear."
        }
        git push
        Note "Push OK -> origin/$(git rev-parse --abbrev-ref HEAD)"
    } else {
        Note "BAJANDO lo remoto..."
        git fetch origin --prune
        git pull --rebase --autostash
        Note "Pull OK -> $(git rev-parse --short HEAD)"
    }
}
catch {
    Note "ERROR: $($_.Exception.Message)"
    exit 1
}
