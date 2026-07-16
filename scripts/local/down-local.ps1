$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path "$PSScriptRoot/../.."

Push-Location $repoRoot
try {
  docker compose --env-file deploy/docker-compose/.env.local `
    -f deploy/docker-compose/base/compose.infrastructure.yml `
    -f deploy/docker-compose/base/compose.application.yml `
    -f deploy/docker-compose/profiles/compose.local-full.yml `
    down --remove-orphans
}
finally {
  Pop-Location
}
