$ErrorActionPreference = 'Continue'

$missing = @()

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  $missing += 'docker'
}
else {
  try {
    docker compose version | Out-Null
  }
  catch {
    $missing += 'docker compose'
  }
}

if ($missing.Count -eq 0) {
  Write-Host 'Prerequisites OK: Docker and Docker Compose are available.'
  exit 0
}

Write-Host 'Missing prerequisites:'
$missing | ForEach-Object { Write-Host "- $_" }
Write-Host ''
Write-Host 'Install Docker Desktop first, then rerun bootstrap-infra.ps1.'
exit 1
