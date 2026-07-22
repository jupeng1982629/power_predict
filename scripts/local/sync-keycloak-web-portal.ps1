[CmdletBinding()]
param(
  [string]$BaseUrl = 'http://localhost:18081',
  [string]$Realm = 'power-predict',
  [string]$ClientId = 'web-portal',
  [string]$AdminRealm = 'master',
  [string]$AdminClientId = 'admin-cli',
  [string]$AdminUsername = 'admin',
  [string]$AdminPassword = 'admin123!'
)

$ErrorActionPreference = 'Stop'

function Merge-UniqueValues {
  param(
    [object[]]$CurrentValues,
    [string[]]$DesiredValues
  )

  $merged = @()
  foreach ($value in @($CurrentValues) + @($DesiredValues)) {
    if ([string]::IsNullOrWhiteSpace([string]$value)) {
      continue
    }

    if ($merged -notcontains [string]$value) {
      $merged += [string]$value
    }
  }

  return $merged
}

$tokenRequest = @{
  Method = 'Post'
  Uri = "$BaseUrl/realms/$AdminRealm/protocol/openid-connect/token"
  ContentType = 'application/x-www-form-urlencoded'
  Body = @{
    grant_type = 'password'
    client_id = $AdminClientId
    username = $AdminUsername
    password = $AdminPassword
  }
}

$tokenResponse = Invoke-RestMethod @tokenRequest

if (-not $tokenResponse.access_token) {
  throw 'Unable to acquire Keycloak admin token.'
}

$headers = @{ Authorization = 'Bearer ' + $tokenResponse.access_token }
$client = Invoke-RestMethod -Method Get -Uri "$BaseUrl/admin/realms/$Realm/clients?clientId=$ClientId" -Headers $headers

if (-not $client -or $client.Count -eq 0) {
  throw "Keycloak client '$ClientId' was not found in realm '$Realm'."
}

$clientConfig = $client[0]
$clientConfig.redirectUris = Merge-UniqueValues -CurrentValues $clientConfig.redirectUris -DesiredValues @(
  'http://localhost:5173/*',
  'http://127.0.0.1:5173/*',
  'http://localhost:5241/*',
  'http://127.0.0.1:5241/*',
  'http://localhost:8001/*',
  'http://127.0.0.1:8001/*',
  'http://localhost:8002/*',
  'http://127.0.0.1:8002/*',
  'http://localhost:8005/*',
  'http://127.0.0.1:8005/*',
  'http://localhost:5260/*',
  'http://127.0.0.1:5260/*'
)
$clientConfig.webOrigins = Merge-UniqueValues -CurrentValues $clientConfig.webOrigins -DesiredValues @(
  'http://localhost:5173',
  'http://127.0.0.1:5173',
  'http://localhost:5241',
  'http://127.0.0.1:5241',
  'http://localhost:8001',
  'http://127.0.0.1:8001',
  'http://localhost:8002',
  'http://127.0.0.1:8002',
  'http://localhost:8005',
  'http://127.0.0.1:8005',
  'http://localhost:5260',
  'http://127.0.0.1:5260'
)

$updateRequest = @{
  Method = 'Put'
  Uri = "$BaseUrl/admin/realms/$Realm/clients/$($clientConfig.id)"
  Headers = $headers
  ContentType = 'application/json'
  Body = ($clientConfig | ConvertTo-Json -Depth 20)
}

Invoke-RestMethod @updateRequest

Write-Host "Keycloak client '$ClientId' redirect URIs and web origins synced successfully."