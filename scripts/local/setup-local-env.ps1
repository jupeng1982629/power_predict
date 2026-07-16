[CmdletBinding()]
param(
  [ValidateSet('local-min', 'local-full')]
  [string]$Profile = 'local-min',

  [switch]$Stop,

  [switch]$Status
)

$ErrorActionPreference = 'Stop'

$script:DockerCommand = $null
$script:ComposeProjectDir = $null

function Test-CommandExists {
  param([Parameter(Mandatory = $true)][string]$Name)
  return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Resolve-DockerCommand {
  if (Test-CommandExists -Name 'docker') {
    return 'docker'
  }

  $fallback = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
  if (Test-Path $fallback) {
    return $fallback
  }

  return $null
}

function Assert-DockerReady {
  $script:DockerCommand = Resolve-DockerCommand
  if (-not $script:DockerCommand) {
    throw 'Docker was not found in PATH. Install Docker Desktop first.'
  }

  # If docker is resolved by absolute path, ensure helper binaries in the same folder are discoverable.
  if ($script:DockerCommand -ne 'docker') {
    $dockerBinDir = Split-Path -Parent $script:DockerCommand
    if ($env:Path -notlike "*$dockerBinDir*") {
      $env:Path = "$dockerBinDir;$env:Path"
    }
  }

  try {
    & $script:DockerCommand compose version | Out-Null
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

function Test-RegistryConnectivity {
  param(
    [Parameter(Mandatory = $true)][string]$Image
  )

  Write-Host "Testing registry connectivity for $Image ..."
  & $script:DockerCommand pull --quiet $Image | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw @"
Unable to pull $Image.
This usually means Docker Desktop proxy settings or your network cannot reach the image registry.
Fix options:
  1. Configure Docker Desktop proxy/mirror settings.
  2. Use a reachable registry mirror by setting POSTGRES_IMAGE, REDIS_IMAGE, MINIO_IMAGE, KAFKA_IMAGE, or MLFLOW_IMAGE in deploy/docker-compose/.env.local.
  3. Verify your network/VPN can access the image registry.
"@
  }
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
    '-f', "deploy/docker-compose/profiles/compose.$Profile.yml",
    '--profile', $Profile
  )

  if ($Profile -eq 'local-full') {
    $composeArgs += @('-f', 'deploy/docker-compose/base/compose.application.yml')
  }

  Push-Location $RepoRoot
  try {
    & $script:DockerCommand compose @composeArgs @ExtraArgs
    if ($LASTEXITCODE -ne 0) {
      throw "docker compose failed with exit code $LASTEXITCODE"
    }
  }
  finally {
    Pop-Location
  }
}

function Get-EnvValueFromFile {
  param(
    [Parameter(Mandatory = $true)][string]$EnvFile,
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$DefaultValue = ''
  )

  $line = Get-Content $EnvFile | Where-Object { $_ -match "^$Name=" } | Select-Object -First 1
  if ($line) {
    return ($line -split '=', 2)[1]
  }

  return $DefaultValue
}

$repoRoot = Resolve-Path "$PSScriptRoot/../.."
Assert-DockerReady
$envFile = Ensure-EnvFile -RepoRoot $repoRoot

$script:ComposeProjectDir = $repoRoot

if (-not $Status -and -not $Stop) {
  $imagesToCheck = @(
    (Get-EnvValueFromFile -EnvFile $envFile -Name 'POSTGRES_IMAGE' -DefaultValue 'postgres:16-alpine'),
    (Get-EnvValueFromFile -EnvFile $envFile -Name 'REDIS_IMAGE' -DefaultValue 'redis:7-alpine'),
    (Get-EnvValueFromFile -EnvFile $envFile -Name 'MINIO_IMAGE' -DefaultValue 'minio/minio:RELEASE.2025-02-03T21-03-04Z'),
    (Get-EnvValueFromFile -EnvFile $envFile -Name 'KAFKA_IMAGE' -DefaultValue 'bitnami/kafka:3.8'),
    (Get-EnvValueFromFile -EnvFile $envFile -Name 'MLFLOW_IMAGE' -DefaultValue 'ghcr.io/mlflow/mlflow:v2.15.1')
  )

  foreach ($image in $imagesToCheck) {
    Test-RegistryConnectivity -Image $image
  }
}

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