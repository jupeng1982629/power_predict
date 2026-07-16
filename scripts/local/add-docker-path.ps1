$ErrorActionPreference = 'Stop'

$dockerBinDir = 'C:\Program Files\Docker\Docker\resources\bin'

if (-not (Test-Path (Join-Path $dockerBinDir 'docker.exe'))) {
  throw 'Docker Desktop binaries were not found at the expected location: C:\Program Files\Docker\Docker\resources\bin'
}

if ($env:Path -notlike "*$dockerBinDir*") {
  $env:Path = "$dockerBinDir;$env:Path"
}

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($userPath -notlike "*$dockerBinDir*") {
  [Environment]::SetEnvironmentVariable('Path', "$userPath;$dockerBinDir", 'User')
}

Write-Host "Docker path is available in the current session and user PATH: $dockerBinDir"
docker version