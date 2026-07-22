param(
  [string]$HostUrl = 'http://localhost:8080',
  [string]$Token = 'local-demo-token',
  [string]$PlantId = 'plant-demo-001',
  [string]$ForecastDate = '2026-07-22'
)

$headers = @{ Authorization = "Bearer $Token" }

function Invoke-JsonGet {
  param([string]$Path)
  $url = "$HostUrl$Path"
  Write-Host "GET $url"
  return Invoke-RestMethod -Uri $url -Headers $headers -Method Get
}

function Invoke-JsonPost {
  param([string]$Path, [hashtable]$Body)
  $url = "$HostUrl$Path"
  Write-Host "POST $url"
  return Invoke-RestMethod -Uri $url -Headers $headers -Method Post -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 8)
}

Invoke-JsonGet '/api/v1/auth/session' | Out-Null
Invoke-JsonGet "/api/v1/plants?pageNo=1&pageSize=3" | Out-Null
Invoke-JsonGet "/api/v1/system/users?pageNo=1&pageSize=3" | Out-Null
Invoke-JsonGet "/api/v1/monitor/plants/$PlantId/generation-records?pageNo=1&pageSize=3" | Out-Null

$train = Invoke-JsonPost '/api/v1/forecast/model-train-jobs' @{
  plantId = $PlantId
  startDate = '2026-06-01'
  endDate = '2026-07-20'
  featureSetVersion = 'feature-v2-xgb'
  algorithm = 'xgboost'
}

$forecast = Invoke-JsonPost '/api/v1/forecast/jobs/dayahead' @{
  plantId = $PlantId
  forecastDate = $ForecastDate
  modelVersion = ''
  weatherSource = 'api'
}

Invoke-JsonGet "/api/v1/forecast/plants/$PlantId/dayahead?forecastDate=$ForecastDate" | Out-Null

Write-Host 'Gateway smoke passed.'
Write-Host "TrainJob: $($train.data.jobId)"
Write-Host "ForecastJob: $($forecast.data.jobId)"
