[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$seedFile = Join-Path $projectRoot 'docker\postgres\demo\seed.sql'

if (-not (Test-Path -LiteralPath $seedFile)) {
    throw "Demo seed file not found: $seedFile"
}

Push-Location $projectRoot
try {
    $runningServices = @(docker compose ps --status running --services)
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to read Docker Compose service status.'
    }
    if ($runningServices -notcontains 'postgres') {
        throw 'PostgreSQL is not running. Start the application before seeding: docker compose --profile app up --build -d'
    }

    $seedSql = Get-Content -LiteralPath $seedFile -Raw -Encoding utf8
    $seedSql | docker compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
    if ($LASTEXITCODE -ne 0) {
        throw 'Demo data seed failed. Confirm the backend has started once so Flyway has initialized the tables.'
    }

    Write-Host 'Demo data is ready for user demo-user.'
}
finally {
    Pop-Location
}
