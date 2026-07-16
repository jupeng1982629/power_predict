[CmdletBinding()]
param(
  [ValidateSet('local-min','local-full')]
  [string]$Profile = 'local-min'
)

$ErrorActionPreference = 'Stop'

function Assert-CommandExists {
  param([Parameter(Mandatory = $true)][string]$Name)

  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "$Name was not found in PATH. Install Docker Desktop or Docker Engine first."
  }
}

Assert-CommandExists -Name 'docker'

try {
  $null = docker compose version
}
catch {
  throw "Docker Compose is not available. Please install Docker Desktop (with Compose v2) and ensure Docker is running."
}

$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$envFile = Join-Path $repoRoot 'deploy/docker-compose/.env.local'
$envExample = Join-Path $repoRoot 'deploy/docker-compose/.env.local.example'

if (-not (Test-Path $envFile)) {
  Copy-Item $envExample $envFile
  Write-Host "Created deploy/docker-compose/.env.local from template."
}

Push-Location $repoRoot
try {
  $composeFiles = @(
    '-f', 'deploy/docker-compose/base/compose.infrastructure.yml',
    '-f', "deploy/docker-compose/profiles/compose.$Profile.yml"
  )

  docker compose --env-file deploy/docker-compose/.env.local @composeFiles --profile $Profile up -d
  docker compose --env-file deploy/docker-compose/.env.local @composeFiles ps
}
finally {
  Pop-Location
}
