[CmdletBinding()]
param(
  [string]$BaseUrl = 'http://localhost:18081',
  [string]$Realm = 'power-predict',
  [string]$ClientId = 'web-portal',
  [string]$Username = 'demo.admin',
  [string]$Password = 'Demo@123456'
)

$ErrorActionPreference = 'Stop'

$tokenUrl = "$BaseUrl/realms/$Realm/protocol/openid-connect/token"
$body = @{
  grant_type = 'password'
  client_id = $ClientId
  username = $Username
  password = $Password
}

$response = Invoke-RestMethod -Method Post -Uri $tokenUrl -ContentType 'application/x-www-form-urlencoded' -Body $body

if (-not $response.access_token) {
  throw 'No access_token returned from Keycloak.'
}

Write-Host ''
Write-Host 'Access token (copy and use as Bearer token):'
Write-Host $response.access_token
Write-Host ''
Write-Host 'Token type:' $response.token_type
Write-Host 'Expires in:' $response.expires_in 'seconds'
