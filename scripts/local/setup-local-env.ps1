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

function Ensure-DefaultImageVariables {
  param([Parameter(Mandatory = $true)][string]$EnvFile)

  $defaults = @{
    'POSTGRES_IMAGE' = 'docker.m.daocloud.io/library/postgres:16-alpine'
    'REDIS_IMAGE'    = 'docker.m.daocloud.io/library/redis:7-alpine'
    'MINIO_IMAGE'    = 'docker.m.daocloud.io/minio/minio:RELEASE.2025-02-03T21-03-04Z'
    'KAFKA_IMAGE'    = 'docker.m.daocloud.io/apache/kafka:3.8.0'
    'MLFLOW_IMAGE'   = 'ghcr.m.daocloud.io/mlflow/mlflow:v2.15.1'
  }

  $content = Get-Content $EnvFile
  $updated = $false

  foreach ($name in $defaults.Keys) {
    if (-not ($content | Where-Object { $_ -match "^$name=" } | Select-Object -First 1)) {
      Add-Content -Path $EnvFile -Value "$name=$($defaults[$name])"
      $updated = $true
    }
  }

  if ($updated) {
    Write-Host 'Patched deploy/docker-compose/.env.local with missing default image variables.'
  }

  $currentKafka = Get-EnvValueFromFile -EnvFile $EnvFile -Name 'KAFKA_IMAGE' -DefaultValue ''
  if ($currentKafka -eq 'docker.m.daocloud.io/bitnami/kafka:3.8') {
    Set-EnvValueInFile -EnvFile $EnvFile -Name 'KAFKA_IMAGE' -Value 'docker.m.daocloud.io/apache/kafka:3.8.0'
    Write-Host 'Updated KAFKA_IMAGE from legacy bitnami mirror to apache mirror default.'
  }
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

function Set-EnvValueInFile {
  param(
    [Parameter(Mandatory = $true)][string]$EnvFile,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Value
  )

  $lines = Get-Content $EnvFile
  $updated = $false
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match "^$Name=") {
      $lines[$i] = "$Name=$Value"
      $updated = $true
      break
    }
  }

  if (-not $updated) {
    $lines += "$Name=$Value"
  }

  Set-Content -Path $EnvFile -Value $lines -Encoding UTF8
}

function Get-FallbackImages {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$CurrentImage
  )

  switch ($Name) {
    'KAFKA_IMAGE' {
      return @(
        $CurrentImage,
        'docker.m.daocloud.io/apache/kafka:3.7.1',
        'dockerproxy.com/apache/kafka:3.8.0',
        'apache/kafka:3.8.0'
      ) | Select-Object -Unique
    }
    default {
      return @($CurrentImage)
    }
  }
}

function Resolve-ImageWithFallback {
  param(
    [Parameter(Mandatory = $true)][string]$EnvFile,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$CurrentImage
  )

  $candidates = Get-FallbackImages -Name $Name -CurrentImage $CurrentImage
  $lastError = $null

  foreach ($candidate in $candidates) {
    try {
      Test-RegistryConnectivity -Image $candidate
      if ($candidate -ne $CurrentImage) {
        Set-EnvValueInFile -EnvFile $EnvFile -Name $Name -Value $candidate
        Write-Host "Updated $Name to fallback image: $candidate"
      }
      return $candidate
    }
    catch {
      $lastError = $_
      Write-Host "Image candidate failed for ${Name}: $candidate"
    }
  }

  throw $lastError
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
Ensure-DefaultImageVariables -EnvFile $envFile

$script:ComposeProjectDir = $repoRoot

if (-not $Status -and -not $Stop) {
  $imageVars = @(
    @{ Name = 'POSTGRES_IMAGE'; Default = 'docker.m.daocloud.io/library/postgres:16-alpine' },
    @{ Name = 'REDIS_IMAGE'; Default = 'docker.m.daocloud.io/library/redis:7-alpine' },
    @{ Name = 'MINIO_IMAGE'; Default = 'docker.m.daocloud.io/minio/minio:RELEASE.2025-02-03T21-03-04Z' },
    @{ Name = 'KAFKA_IMAGE'; Default = 'docker.m.daocloud.io/apache/kafka:3.8.0' },
    @{ Name = 'MLFLOW_IMAGE'; Default = 'ghcr.m.daocloud.io/mlflow/mlflow:v2.15.1' }
  )

  foreach ($item in $imageVars) {
    $current = Get-EnvValueFromFile -EnvFile $envFile -Name $item.Name -DefaultValue $item.Default
    Resolve-ImageWithFallback -EnvFile $envFile -Name $item.Name -CurrentImage $current | Out-Null
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