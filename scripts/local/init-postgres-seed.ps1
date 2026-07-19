[CmdletBinding()]
param(
  [string]$ContainerName = 'pp-postgres',
  [string]$Database = 'power_predict',
  [string]$User = 'power_predict'
)

$ErrorActionPreference = 'Stop'

function Resolve-DockerCommand {
  $fallback = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'

  if (Get-Command docker -ErrorAction SilentlyContinue) {
    return 'docker'
  }

  if (Test-Path $fallback) {
    return $fallback
  }

  throw 'Docker was not found. Install Docker Desktop or ensure docker is in PATH.'
}

$docker = Resolve-DockerCommand
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '../..')
$sqlFile = Join-Path $repoRoot 'sql/postgresql/002_seed_demo_data.sql'

if (-not (Test-Path $sqlFile)) {
  throw ('SQL file not found: ' + $sqlFile)
}

$containerRunning = & $docker inspect -f '{{.State.Running}}' $ContainerName 2>$null
if ($LASTEXITCODE -ne 0 -or $containerRunning -ne 'true') {
  throw ('Container ' + $ContainerName + ' is not running. Start the local infrastructure first with .\scripts\local\setup-local-env.cmd')
}

$tempContainerFile = '/tmp/002_seed_demo_data.sql'
$containerSqlFile = "$ContainerName`:$tempContainerFile"

$copyProcess = Start-Process -FilePath $docker -ArgumentList @('cp', $sqlFile, $containerSqlFile) -NoNewWindow -PassThru -Wait
if ($copyProcess.ExitCode -ne 0) {
  throw ('Copying SQL file into the container failed with exit code ' + $copyProcess.ExitCode + '.')
}

$execProcess = Start-Process -FilePath $docker -ArgumentList @('exec', '-i', $ContainerName, 'psql', '-U', $User, '-d', $Database, '-v', 'ON_ERROR_STOP=1', '-f', $tempContainerFile) -NoNewWindow -PassThru -Wait
if ($execProcess.ExitCode -ne 0) {
  throw ('PostgreSQL seed data import failed with exit code ' + $execProcess.ExitCode + '.')
}

Write-Host ''
Write-Host 'PostgreSQL seed data import completed successfully.'