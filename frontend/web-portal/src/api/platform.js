function buildHeaders(auth) {
  return {
    Authorization: `Bearer ${auth.token}`,
    'X-Debug-User': auth.userId,
    'X-Debug-Name': auth.userName,
    'X-Debug-Tenant': auth.tenantId,
    'X-Debug-Roles': auth.roles.join(','),
    'Content-Type': 'application/json',
  }
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`)
  }
  return response.json()
}

export function defaultAuthContext() {
  return {
    token: 'local-demo-token',
    userId: 'user-demo-admin',
    userName: 'Demo Admin',
    tenantId: 'tenant-demo',
    roles: ['forecast:read', 'forecast:run', 'system:admin', 'plant:read'],
  }
}

export function fetchSession(auth) {
  return requestJson('/api/v1/auth/session', {
    headers: buildHeaders(auth),
  })
}

export function fetchOverview(plantId, auth) {
  return requestJson(`/api/v1/dashboard/overview?plantId=${encodeURIComponent(plantId)}`, {
    headers: buildHeaders(auth),
  })
}

export function fetchForecasts(plantId, forecastDate, auth) {
  return requestJson(`/api/v1/plants/${encodeURIComponent(plantId)}/forecasts?forecastDate=${encodeURIComponent(forecastDate)}`, {
    headers: buildHeaders(auth),
  })
}

export function fetchActuals(plantId, forecastDate, auth) {
  return requestJson(`/api/v1/plants/${encodeURIComponent(plantId)}/actuals?forecastDate=${encodeURIComponent(forecastDate)}`, {
    headers: buildHeaders(auth),
  })
}

export function fetchEvaluations(plantId, auth) {
  return requestJson(`/api/v1/plants/${encodeURIComponent(plantId)}/evaluations`, {
    headers: buildHeaders(auth),
  })
}

export function runDayAheadForecast(plantId, forecastDate, auth) {
  return requestJson('/api/v1/jobs/forecast-dayahead', {
    method: 'POST',
    headers: buildHeaders(auth),
    body: JSON.stringify({ plantId, forecastDate }),
  })
}