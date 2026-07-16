$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$envFile = Join-Path $repoRoot "deploy/docker-compose/.env.local"
$envExample = Join-Path $repoRoot "deploy/docker-compose/.env.local.example"

if (!(Test-Path $envFile)) {
  Copy-Item $envExample $envFile
  Write-Host "Created deploy/docker-compose/.env.local from template."
}

Push-Location $repoRoot
try {
  docker compose --env-file deploy/docker-compose/.env.local `
    -f deploy/docker-compose/base/compose.infrastructure.yml `
    -f deploy/docker-compose/profiles/compose.local-min.yml `
    --profile local-min up -d

  docker compose --env-file deploy/docker-compose/.env.local `
    -f deploy/docker-compose/base/compose.infrastructure.yml `
    ps
}
finally {
  Pop-Location
}
