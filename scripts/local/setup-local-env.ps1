[CmdletBinding()]
param(
  [ValidateSet('local-min', 'local-full')]
  [string]$Profile = 'local-min',

  [switch]$Stop,

  [switch]$Status
)

$ErrorActionPreference = 'Stop'

$script:DockerCommand = $null

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