import http from '@/utils/httpRequest'

export function getSession () {
  return http({
    url: http.adornUrl('/api/v1/auth/session'),
    method: 'get',
    params: http.adornParams()
  })
}

export function getPlants (params = {}) {
  return http({
    url: http.adornUrl('/api/v1/plants'),
    method: 'get',
    params: http.adornParams(params, false)
  })
}

export function getPlant (plantId) {
  return http({
    url: http.adornUrl(`/api/v1/plants/${plantId}`),
    method: 'get'
  })
}

export function createPlant (data) {
  return http({
    url: http.adornUrl('/api/v1/plants'),
    method: 'post',
    data: http.adornData(data, false)
  })
}

export function updatePlant (plantId, data) {
  return http({
    url: http.adornUrl(`/api/v1/plants/${plantId}`),
    method: 'put',
    data: http.adornData(data, false)
  })
}

export function deletePlant (plantId) {
  return http({
    url: http.adornUrl(`/api/v1/plants/${plantId}`),
    method: 'delete'
  })
}

export function getForecasts (plantId, forecastDate) {
  return http({
    url: http.adornUrl(`/api/v1/plants/${plantId}/forecasts`),
    method: 'get',
    params: http.adornParams({ forecastDate }, false)
  })
}

export function getActuals (plantId, forecastDate) {
  return http({
    url: http.adornUrl(`/api/v1/plants/${plantId}/actuals`),
    method: 'get',
    params: http.adornParams({ forecastDate }, false)
  })
}

export function getOverview (plantId) {
  return http({
    url: http.adornUrl('/api/v1/dashboard/overview'),
    method: 'get',
    params: http.adornParams({ plantId }, false)
  })
}
