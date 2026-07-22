# 服务 API 契约设计

## 1. 文档目标

本文件定义对外 API 契约、服务间调用契约、认证授权规范、错误码与版本策略，作为前后端联调与网关治理基线。

## 2. 通用契约规范

## 2.1 协议与风格

1. 协议：HTTPS。
2. 风格：RESTful + JSON。
3. 版本：URI 前缀版本化，统一 `v1`。
4. 时间格式：ISO-8601（UTC 或带时区偏移）。

## 2.2 网关前缀

统一入口前缀：`/api/v1`

域前缀建议：

1. `/system/*`
2. `/plants/*`
3. `/monitor/*`
4. `/forecast/*`
5. `/ops/*`

## 2.3 统一响应体

```json
{
  "code": "0",
  "message": "OK",
  "traceId": "6f3c2f7a1c9e4b73",
  "data": {}
}
```

分页响应：

```json
{
  "code": "0",
  "message": "OK",
  "traceId": "6f3c2f7a1c9e4b73",
  "data": {
    "items": [],
    "pageNo": 1,
    "pageSize": 20,
    "total": 128
  }
}
```

## 2.4 错误码规范

1. `AUTH_401`: 未认证或 Token 无效。
2. `AUTH_403`: 权限不足。
3. `REQ_400`: 参数校验失败。
4. `RES_404`: 资源不存在。
5. `BIZ_409`: 业务冲突（重复任务等）。
6. `SYS_500`: 系统异常。

## 3. 认证与授权契约

## 3.1 OAuth2/OIDC 方案

1. IdP：Keycloak。
2. 前端：Authorization Code + PKCE。
3. 服务：Resource Server JWT 验签。
4. 服务间：client_credentials。

## 3.2 Header 约定

1. `Authorization: Bearer <access_token>`。
2. `X-Trace-Id: <uuid>`（可选，网关可自动生成）。
3. `X-Tenant-Id: <tenant_id>`（多租户场景由网关注入）。

## 3.3 Scope 建议

1. `plant:read`、`plant:write`
2. `monitor:read`
3. `forecast:read`、`forecast:run`
4. `model:read`、`model:publish`
5. `system:admin`

## 4. 平台 API 契约

## 4.1 电站管理（plant-service）

1. `GET /api/v1/plants`
- Query: `pageNo`, `pageSize`, `keyword`, `status`
- Scope: `plant:read`

2. `POST /api/v1/plants`
- Scope: `plant:write`
- 请求体：

```json
{
  "plantName": "华北示范站A",
  "capacityMw": 120.5,
  "latitude": 39.903,
  "longitude": 116.391,
  "timezone": "Asia/Shanghai"
}
```

3. `GET /api/v1/plants/{plantId}`
- Scope: `plant:read`

4. `PUT /api/v1/plants/{plantId}`
- Scope: `plant:write`

5. `DELETE /api/v1/plants/{plantId}`
- Scope: `plant:write`
- 逻辑删除，保留审计记录。

## 4.2 实时监测（monitor-service）

1. `GET /api/v1/monitor/plants/{plantId}/realtime`
- Query: `at`（可选，默认当前）
- Scope: `monitor:read`

返回字段要求（采集侧）：

1. 功率字段：`activePowerKw`, `reactivePowerKvar`, `apparentPowerKva`（可选）。
2. 功率因数字段：`powerFactorTotal`，可选 `powerFactorPhaseA/B/C`。
3. 三相电压电流：`voltagePhaseA/B/C`, `currentPhaseA/B/C`。
4. 发电量字段：`energy15mKwh`, `energyDailyKwh`。
5. 扩展电气参数：`frequencyHz`, `inverterTempC`, `deviceStatusCode`（可选）。

返回示例：

```json
{
  "code": "0",
  "message": "OK",
  "traceId": "a4d219c3e7f24ad5",
  "data": {
    "plantId": "P1001",
    "timestamp": "2026-07-16T10:15:00+08:00",
    "activePowerKw": 52340.2,
    "reactivePowerKvar": 1120.5,
    "powerFactorTotal": 0.98,
    "voltagePhaseA": 380.2,
    "voltagePhaseB": 379.8,
    "voltagePhaseC": 381.1,
    "currentPhaseA": 92.4,
    "currentPhaseB": 91.7,
    "currentPhaseC": 93.1,
    "energy15mKwh": 12835.6,
    "energyDailyKwh": 256780.4,
    "onlineDeviceRatio": 0.98,
    "alarmCount": 2
  }
}
```

2. `GET /api/v1/monitor/plants/{plantId}/timeseries`
- Query: `startTime`, `endTime`, `granularity`, `metrics[]`
- Scope: `monitor:read`

其中 `metrics[]` 可选值建议包括：

1. `activePowerKw`, `reactivePowerKvar`, `powerFactorTotal`
2. `voltagePhaseA`, `voltagePhaseB`, `voltagePhaseC`
3. `currentPhaseA`, `currentPhaseB`, `currentPhaseC`
4. `energy15mKwh`, `energyDailyKwh`

## 4.3 预测管理（forecast-service）

1. `POST /api/v1/forecast/jobs/dayahead`
- Scope: `forecast:run`
- 请求体：

```json
{
  "plantId": "P1001",
  "forecastDate": "2026-07-17",
  "modelName": "lgbm-dayahead",
  "modelVersion": "v1.2.0",
  "idempotencyKey": "P1001-2026-07-17-manual"
}
```

2. `GET /api/v1/forecast/jobs/{jobId}`
- Scope: `forecast:read`

3. `GET /api/v1/forecast/plants/{plantId}/dayahead`
- Query: `forecastDate`, `modelVersion`
- Scope: `forecast:read`

4. `GET /api/v1/forecast/plants/{plantId}/evaluation`
- Query: `startDate`, `endDate`, `modelName`
- Scope: `forecast:read`

## 4.4 系统管理（system-service）

1. `GET /api/v1/system/users`
2. `POST /api/v1/system/users`
3. `GET /api/v1/system/roles`
4. `POST /api/v1/system/roles`
5. `POST /api/v1/system/role-bindings`

Scope 要求：`system:admin`

## 5. 服务间内部 API 契约

## 5.1 forecast-service -> inference-service

1. `POST /internal/v1/inference/dayahead`
- 鉴权：`client_credentials`。
- 超时：3s 建议 + 指数退避重试。
- 请求示例：

```json
{
  "plantId": "P1001",
  "forecastDate": "2026-07-17",
  "modelName": "lgbm-dayahead",
  "modelVersion": "v1.2.0"
}
```

## 5.2 inference-service -> feature-service

1. `GET /internal/v1/features/snapshot`
- 参数：`plantId`, `asOfTime`, `featureVersion`

## 5.3 model-service -> forecast-service

1. `POST /internal/v1/models/publish-event`
- 发布模型后通知 forecast-service 更新可用版本缓存。

## 6. 幂等、限流与重试

1. 对触发类接口支持 `Idempotency-Key`。
2. 网关按 `client_id + path` 限流。
3. 读接口默认可重试，写接口只在幂等键存在时重试。
4. 任务创建采用唯一约束避免重复提交。

## 7. API 兼容性策略

1. `v1` 内只做向后兼容增量字段。
2. 删除字段必须先废弃至少一个小版本周期。
3. 不兼容变更进入 `v2`。

## 8. OpenAPI 产物建议

每个服务维护 `openapi.yaml`，网关汇聚生成平台 API 门户：

1. `backend/java/plant-service/openapi.yaml`
2. `backend/java/monitor-service/openapi.yaml`
3. `backend/java/forecast-service/openapi.yaml`
4. `backend/java/system-service/openapi.yaml`
5. `backend/python/inference-service/openapi.yaml`
