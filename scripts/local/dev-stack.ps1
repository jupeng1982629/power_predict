[CmdletBinding()]
param(
  [ValidateSet('start', 'stop', 'status')]
  [string]$Action = 'start',

  [ValidateSet('local-min', 'local-full')]
  [string]$InfraProfile = 'local-min',

  [switch]$SkipInfra,
  [switch]$SkipSchemaInit,
  [switch]$SkipSeedInit,
  [switch]$SkipWebInstall,
  [switch]$SkipPythonInstall,
  [switch]$SkipJavaBuild,
  [switch]$IncludeInfra
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Resolve-Path "$PSScriptRoot/../.."
$RunRoot = Join-Path $RepoRoot '.run/dev-stack'
$LogRoot = Join-Path $RunRoot 'logs'
$PidFile = Join-Path $RunRoot 'pids.json'

$JavaHome = Join-Path $RepoRoot '.jdk/jdk-17.0.18'
$JavaBin = Join-Path $JavaHome 'bin'

function Ensure-RunFolders {
  New-Item -ItemType Directory -Force -Path $RunRoot | Out-Null
  New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null
}

function Quote-Single {
  param([Parameter(Mandatory = $true)][string]$Text)
  return $Text.Replace("'", "''")
}

function Assert-Command {
  param([Parameter(Mandatory = $true)][string]$Name)
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "$Name was not found in PATH."
  }
}

function Resolve-PythonExe {
  $candidates = @(
    'C:\Users\jjjpp\AppData\Local\Microsoft\WindowsApps\python3.12.exe',
    'C:\Users\jjjpp\AppData\Local\Programs\Python\Python312\python.exe'
  )

  foreach ($candidate in $candidates) {
    if (Test-Path $candidate) {
      return $candidate
    }
  }

  throw 'Python 3.12 was not found. Install Python 3.12 or adjust Resolve-PythonExe in scripts/local/dev-stack.ps1.'
}

function Invoke-RepoScript {
  param(
    [Parameter(Mandatory = $true)][string]$ScriptPath,
    [string[]]$Arguments = @()
  )

  $fullPath = Join-Path $RepoRoot $ScriptPath
  if (-not (Test-Path $fullPath)) {
    throw "Script not found: $fullPath"
  }

  & powershell -NoProfile -ExecutionPolicy Bypass -File $fullPath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Failed running script: $ScriptPath"
  }
}

function Get-PidsByPort {
  param([Parameter(Mandatory = $true)][int]$Port)

  $result = @()
  $lines = netstat -ano | Select-String -Pattern ":$Port\s"
  foreach ($line in $lines) {
    $parts = ($line.ToString() -replace '\s+', ' ').Trim().Split(' ')
    if ($parts.Length -gt 0) {
      $procId = $parts[$parts.Length - 1]
      if ($procId -match '^[0-9]+$' -and $procId -ne '0') {
        $result += [int]$procId
      }
    }
  }

  return $result | Select-Object -Unique
}

function Stop-ProcessesByPorts {
  param([int[]]$Ports)

  foreach ($port in $Ports) {
    $pids = Get-PidsByPort -Port $port
    foreach ($pidValue in $pids) {
      try {
        Stop-Process -Id $pidValue -Force -ErrorAction Stop
        Write-Host "Stopped PID=$pidValue on port $port"
      }
      catch {
        Write-Host ("Skip PID={0} on port {1}: {2}" -f $pidValue, $port, $_.Exception.Message)
      }
    }
  }
}

function Stop-ManagedProcesses {
  if (Test-Path $PidFile) {
    try {
      $state = Get-Content $PidFile -Raw | ConvertFrom-Json
      foreach ($entry in $state.processes) {
        if ($null -ne $entry.pid) {
          try {
            Stop-Process -Id ([int]$entry.pid) -Force -ErrorAction Stop
            Write-Host "Stopped $($entry.name) PID=$($entry.pid)"
          }
          catch {
            Write-Host ("Skip {0} PID={1}: {2}" -f $entry.name, $entry.pid, $_.Exception.Message)
          }
        }
      }
    }
    finally {
      Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
    }
  }

  Stop-ProcessesByPorts -Ports @(8081, 8080, 8002, 8003, 5173)
}

function Start-ManagedProcess {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$WorkDir,
    [Parameter(Mandatory = $true)][string]$Command,
    [hashtable]$Env = @{}
  )

  $logOut = Join-Path $LogRoot "$Name.out.log"
  $logErr = Join-Path $LogRoot "$Name.err.log"

  $scriptLines = @(
    '$ErrorActionPreference = ''Stop''',
    "Set-Location '$(Quote-Single -Text $WorkDir)'"
  )

  foreach ($key in $Env.Keys) {
    $value = [string]$Env[$key]
    $scriptLines += ("`$env:{0} = '{1}'" -f $key, (Quote-Single -Text $value))
  }

  $scriptLines += $Command
  $inlineScript = $scriptLines -join '; '

  $proc = Start-Process -FilePath 'powershell.exe' `
    -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $inlineScript) `
    -WorkingDirectory $WorkDir `
    -RedirectStandardOutput $logOut `
    -RedirectStandardError $logErr `
    -PassThru

  Write-Host "Started $Name PID=$($proc.Id)"

  return [pscustomobject]@{
    name = $Name
    pid = $proc.Id
    workingDirectory = $WorkDir
    command = $Command
    stdoutLog = $logOut
    stderrLog = $logErr
  }
}

function Test-Http {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Url,
    [hashtable]$Headers = @{}
  )

  try {
    $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $Url -Headers $Headers -TimeoutSec 4
    return [pscustomobject]@{ service = $Name; status = 'up'; code = $response.StatusCode; url = $Url }
  }
  catch {
    $code = 'n/a'
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $code = [int]$_.Exception.Response.StatusCode
    }
    return [pscustomobject]@{ service = $Name; status = 'down'; code = $code; url = $Url }
  }
}

function Check-Dependencies {
  param(
    [Parameter(Mandatory = $true)][string]$PythonExe,
    [switch]$InstallPythonDeps,
    [switch]$InstallWebDeps
  )

  Assert-Command -Name 'powershell'
  Assert-Command -Name 'mvn'
  Assert-Command -Name 'npm'

  if (-not (Test-Path (Join-Path $JavaBin 'java.exe'))) {
    throw "JDK 17 not found at $JavaHome"
  }

  if (-not $SkipInfra) {
    Assert-Command -Name 'docker'
    try {
      docker compose version | Out-Null
    }
    catch {
      throw 'Docker Compose is unavailable. Start Docker Desktop first.'
    }
  }

  $pythonImportCheck = "import fastapi, uvicorn, psycopg, numpy, xgboost, sklearn, orjson; print('PY_DEPS_OK')"
  & $PythonExe -c $pythonImportCheck | Out-Null

  if ($LASTEXITCODE -ne 0) {
    if (-not $InstallPythonDeps) {
      throw 'Python dependencies are missing. Re-run without -SkipPythonInstall to auto-install.'
    }

    Write-Host 'Installing Python dependencies for model-service/inference-service ...'
    $pythonDeps = @('fastapi', 'uvicorn', 'psycopg[binary]', 'numpy', 'orjson', 'xgboost', 'scikit-learn')
    & $PythonExe -m pip install --user @pythonDeps
    if ($LASTEXITCODE -ne 0) {
      throw 'Failed to install Python dependencies.'
    }

    & $PythonExe -c $pythonImportCheck | Out-Null
    if ($LASTEXITCODE -ne 0) {
      throw 'Python dependency check still failed after installation.'
    }
  }

  $webNodeModules = Join-Path $RepoRoot 'frontend/web-portal/node_modules'
  if (-not (Test-Path $webNodeModules)) {
    if (-not $InstallWebDeps) {
      throw 'Web dependencies are missing. Re-run without -SkipWebInstall to auto-install.'
    }

    Write-Host 'Installing frontend dependencies ...'
    Push-Location (Join-Path $RepoRoot 'frontend/web-portal')
    try {
      npm install
      if ($LASTEXITCODE -ne 0) {
        throw 'npm install failed.'
      }
    }
    finally {
      Pop-Location
    }
  }
}

function Build-Java {
  Push-Location (Join-Path $RepoRoot 'backend/java')
  try {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaBin;$env:Path"
    mvn -q -pl common-libs,system-service,gateway-service -am clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
      throw 'Maven build failed.'
    }
  }
  finally {
    Pop-Location
  }
}

function Save-State {
  param([Parameter(Mandatory = $true)][object[]]$Processes)

  $state = [pscustomobject]@{
    startedAt = (Get-Date).ToString('o')
    processes = $Processes
  }

  $state | ConvertTo-Json -Depth 6 | Set-Content -Path $PidFile -Encoding UTF8
}

function Show-Status {
  $headers = @{ Authorization = 'Bearer local-demo-token' }
  $checks = @(
    (Test-Http -Name 'system-service' -Url 'http://127.0.0.1:8081/actuator/health'),
    (Test-Http -Name 'gateway-service' -Url 'http://127.0.0.1:8080/actuator/health'),
    (Test-Http -Name 'model-service' -Url 'http://127.0.0.1:8002/health'),
    (Test-Http -Name 'inference-service' -Url 'http://127.0.0.1:8003/health'),
    (Test-Http -Name 'web-portal' -Url 'http://127.0.0.1:5173'),
    (Test-Http -Name 'gateway-forecast-read' -Url 'http://127.0.0.1:8080/api/v1/plants/plant-demo-001/forecasts?forecastDate=2026-07-17' -Headers $headers)
  )

  Write-Host ''
  Write-Host 'Service status:'
  $checks | Format-Table -AutoSize

  if (Test-Path $PidFile) {
    Write-Host ''
    Write-Host "Managed process state: $PidFile"
  }
  else {
    Write-Host ''
    Write-Host 'Managed process state: not found'
  }
}

Ensure-RunFolders

if ($Action -eq 'stop') {
  Stop-ManagedProcesses
  if ($IncludeInfra -and -not $SkipInfra) {
    Invoke-RepoScript -ScriptPath 'scripts/local/setup-local-env.ps1' -Arguments @('-Profile', $InfraProfile, '-Stop')
  }
  Show-Status
  return
}

if ($Action -eq 'status') {
  Show-Status
  return
}

$pythonExe = Resolve-PythonExe

Check-Dependencies -PythonExe $pythonExe -InstallPythonDeps:(-not $SkipPythonInstall) -InstallWebDeps:(-not $SkipWebInstall)

if (-not $SkipInfra) {
  Invoke-RepoScript -ScriptPath 'scripts/local/setup-local-env.ps1' -Arguments @('-Profile', $InfraProfile)
}

if (-not $SkipSchemaInit) {
  Invoke-RepoScript -ScriptPath 'scripts/local/init-postgres-schema.ps1'
}

if (-not $SkipSeedInit) {
  Invoke-RepoScript -ScriptPath 'scripts/local/init-postgres-seed.ps1'
}

if (-not $SkipJavaBuild) {
  Build-Java
}

Stop-ManagedProcesses

$javaEnv = @{
  JAVA_HOME = $JavaHome
  PATH = "$JavaBin;$env:Path"
}

$pythonCmdForScript = "& '$(Quote-Single -Text $pythonExe)'"

$processes = @()
$processes += Start-ManagedProcess -Name 'system-service' -WorkDir (Join-Path $RepoRoot 'backend/java/system-service') -Command 'mvn spring-boot:run' -Env $javaEnv
$processes += Start-ManagedProcess -Name 'gateway-service' -WorkDir (Join-Path $RepoRoot 'backend/java/gateway-service') -Command 'mvn spring-boot:run' -Env $javaEnv
$processes += Start-ManagedProcess -Name 'model-service' -WorkDir (Join-Path $RepoRoot 'backend/python/model-service') -Command "$pythonCmdForScript -m uvicorn app.main:app --host 0.0.0.0 --port 8002" -Env @{
  POSTGRES_HOST = 'localhost'
  POSTGRES_PORT = '5432'
  POSTGRES_DB = 'power_predict'
  POSTGRES_USER = 'power_predict'
  POSTGRES_PASSWORD = 'power_predict'
}
$processes += Start-ManagedProcess -Name 'inference-service' -WorkDir (Join-Path $RepoRoot 'backend/python/inference-service') -Command "$pythonCmdForScript -m uvicorn app.main:app --host 0.0.0.0 --port 8003" -Env @{
  POSTGRES_HOST = 'localhost'
  POSTGRES_PORT = '5432'
  POSTGRES_DB = 'power_predict'
  POSTGRES_USER = 'power_predict'
  POSTGRES_PASSWORD = 'power_predict'
}
$processes += Start-ManagedProcess -Name 'web-portal' -WorkDir (Join-Path $RepoRoot 'frontend/web-portal') -Command 'npm run dev'

Save-State -Processes $processes

Show-Status

Write-Host ''
Write-Host 'Dev stack start completed.'
Write-Host "Logs directory: $LogRoot"
Write-Host 'Use scripts/local/dev-stack.cmd -Action stop to close all managed services.'
