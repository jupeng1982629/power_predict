[CmdletBinding()]
param(
  [ValidateSet('local-min', 'local-full')]
  [string]$Profile = 'local-min',

  [switch]$Stop,

  [switch]$Status
)

$ErrorActionPreference = 'Stop'

function Test-CommandExists {
  param([Parameter(Mandatory = $true)][string]$Name)
  return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Assert-DockerReady {
  if (-not (Test-CommandExists -Name 'docker')) {
    throw 'Docker was not found in PATH. Install Docker Desktop first.'
  }

  try {
    docker compose version | Out-Null
  }
  catch {
    throw 'Docker Compose is not available. Install Docker Desktop with Compose v2 and make sure Docker is running.'
  }
}

function Ensure-EnvFile {
  param([Parameter(Mandatory = $true)][string]$RepoRoot)

  $envFile = Join-Path $RepoRoot 'deploy/docker-compose/.env.local'
  $envExample = Join-Path $RepoRoot 'deploy/docker-compose/.env.local.example'

  if (-not (Test-Path $envFile)) {
    Copy-Item $envExample $envFile
    Write-Host 'Created deploy/docker-compose/.env.local from template.'
  }

  return $envFile
}

function Invoke-Compose {
  param(
    [Parameter(Mandatory = $true)][string]$RepoRoot,
    [Parameter(Mandatory = $true)][string]$Profile,
    [Parameter(Mandatory = $true)][string[]]$ExtraArgs
  )

  $composeArgs = @(
    '--env-file', 'deploy/docker-compose/.env.local',
    '-f', 'deploy/docker-compose/base/compose.infrastructure.yml',
    '-f', "deploy/docker-compose/profiles/compose.$Profile.yml"
  )

  if ($Profile -eq 'local-full') {
    $composeArgs += @('-f', 'deploy/docker-compose/base/compose.application.yml')
  }

  Push-Location $RepoRoot
  try {
    docker compose @composeArgs @ExtraArgs
  }
  finally {
    Pop-Location
  }
}

$repoRoot = Resolve-Path "$PSScriptRoot/../.."
Assert-DockerReady
Ensure-EnvFile -RepoRoot $repoRoot | Out-Null

if ($Stop) {
  Invoke-Compose -RepoRoot $repoRoot -Profile $Profile -ExtraArgs @('down', '--remove-orphans')
  return
}

if ($Status) {
  Invoke-Compose -RepoRoot $repoRoot -Profile $Profile -ExtraArgs @('ps')
  return
}

Invoke-Compose -RepoRoot $repoRoot -Profile $Profile -ExtraArgs @('up', '-d')
Invoke-Compose -RepoRoot $repoRoot -Profile $Profile -ExtraArgs @('ps')

Write-Host ''
Write-Host 'Local infrastructure is ready.'
Write-Host 'Common endpoints:'
Write-Host '  PostgreSQL: localhost:5432'
Write-Host '  Redis:      localhost:6379'
Write-Host '  MinIO API:  localhost:9000'
Write-Host '  MinIO UI:   localhost:9001'
Write-Host '  Kafka:      localhost:9092'
Write-Host '  MLflow:     localhost:5000'