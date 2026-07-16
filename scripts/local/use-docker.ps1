$ErrorActionPreference = 'Stop'

$dockerBinDir = 'C:\Program Files\Docker\Docker\resources\bin'

if (-not (Test-Path (Join-Path $dockerBinDir 'docker.exe'))) {
  throw 'Docker Desktop binaries were not found at the expected install path. Please reinstall Docker Desktop or update the path in scripts/local/use-docker.ps1.'
}

if ($env:Path -notlike "*$dockerBinDir*") {
  $env:Path = "$dockerBinDir;$env:Path"
}

Write-Host "Added Docker to current session PATH: $dockerBinDir"

docker version