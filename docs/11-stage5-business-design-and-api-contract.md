# Stage 5 业务设计与接口契约（资源管理 + 数据采集 + 训练 + 预测）

## 1. 目标

基于当前 Stage 4 已完成的认证鉴权底座，进入 Stage 5 的目标是：

1. 将业务功能按领域拆分为可独立交付的模块。
2. 明确 Java 服务与 Python 服务的边界和协作方式。
3. 固化前后端联调接口契约，作为后续编码基线。
4. 确保“每个光伏电站有独立模型”的业务规则可落地。

## 2. 业务范围拆分

### 2.1 资源管理域

1. 光伏电站档案管理（CRUD）。
2. 用户管理（CRUD）。
3. 角色管理（CRUD）与权限定义（角色-权限绑定）。

### 2.2 数据采集管理域

1. 电站实时发电量数据列表与人工 CRUD。
2. 天气采集数据列表与人工 CRUD。
3. 两类数据都支持 Excel 导入。
4. 发电采集数据必须支持三相电与功率质量参数：A/B/C 三相电压电流、总有功功率、总无功功率、总功率因数。
5. 发电量口径必须同时支持当日累计发电量与 15 分钟时段发电量。

### 2.3 模型训练域

1. 用户选择电站。
2. 用户选择训练时间范围。
3. 训练样本由发电数据 + 电气数据 + 天气数据组成。
4. 按电站训练独立模型并登记版本。

### 2.4 发电量预测域

1. 用户选择电站与预测日期。
2. 系统按电站地理位置调用天气服务 API 获取天气。
3. 载入该电站当前可用模型执行预测。
4. 返回预测结果并支持查询任务记录。

## 3. 服务职责与边界变更

## 3.1 Java 服务

1. gateway-service
- 统一外部 API 入口。
- 承载认证、鉴权、审计、限流。
- 按域路由到 plant/system/monitor/forecast。

2. plant-service
- 维护电站档案主数据（SoT）。
- 提供电站 CRUD 和地理信息查询。

3. system-service
- 维护用户、角色、权限（SoT）。
- 提供用户/角色/权限 CRUD 与绑定。

4. monitor-service
- 维护发电量与天气观测数据（业务侧 SoT）。
- 提供数据列表、人工 CRUD、Excel 导入任务。

5. forecast-service
- 训练任务编排与预测任务编排。
- 管理“电站-模型版本”的生产版本映射。
- 作为 Java 域到 Python 域的编排入口。

## 3.2 Python 服务

1. feature-service
- 按电站 + 时间范围生成训练特征快照。
- 为训练与推理提供一致口径特征转换。

2. model-service
- 接收训练请求并执行训练。
- 将模型登记到 MLflow。
- 输出训练指标、模型版本、特征版本。

3. inference-service
- 按电站加载生产模型。
- 基于天气与必要历史特征执行预测。
- 返回预测点列与置信元信息。

## 4. API 设计（对外）

统一前缀：`/api/v1`

## 4.1 资源管理 API

### 4.1.1 电站管理（plant-service）

1. `GET /plants`
- Query: `pageNo`, `pageSize`, `keyword`, `status`

2. `POST /plants`

3. `GET /plants/{plantId}`

4. `PUT /plants/{plantId}`

5. `DELETE /plants/{plantId}`
- 逻辑删除，保留审计。

### 4.1.2 用户管理（system-service）

1. `GET /system/users`
2. `POST /system/users`
3. `GET /system/users/{userId}`
4. `PUT /system/users/{userId}`
5. `DELETE /system/users/{userId}`

### 4.1.3 角色与权限（system-service）

1. `GET /system/roles`
2. `POST /system/roles`
3. `PUT /system/roles/{roleId}`
4. `DELETE /system/roles/{roleId}`
5. `GET /system/permissions`
6. `PUT /system/roles/{roleId}/permissions`
- Body: `permissionCodes[]`

## 4.2 数据采集 API（monitor-service）

### 4.2.1 发电量数据

1. `GET /monitor/plants/{plantId}/generation-records`
- Query: `startTime`, `endTime`, `pageNo`, `pageSize`

2. `POST /monitor/plants/{plantId}/generation-records`

3. `PUT /monitor/plants/{plantId}/generation-records/{recordId}`

4. `DELETE /monitor/plants/{plantId}/generation-records/{recordId}`

5. `POST /monitor/plants/{plantId}/generation-records:import`
- `multipart/form-data` 上传 Excel。

字段建议：

1. 时间与标识：`recordTime`, `plantId`, `deviceId`（可选）。
2. 功率：`activePowerKw`, `reactivePowerKvar`, `apparentPowerKva`（可选）。
3. 功率因数：`powerFactorTotal`，可选 `powerFactorPhaseA/B/C`。
4. 三相电压：`voltagePhaseA`, `voltagePhaseB`, `voltagePhaseC`。
5. 三相电流：`currentPhaseA`, `currentPhaseB`, `currentPhaseC`。
6. 发电量：`energy15mKwh`, `energyDailyKwh`。
7. 其他电气参数：`frequencyHz`, `inverterTempC`, `deviceStatusCode`（按现场设备能力可选）。

### 4.2.2 天气数据

1. `GET /monitor/plants/{plantId}/weather-records`
- Query: `startTime`, `endTime`, `pageNo`, `pageSize`

2. `POST /monitor/plants/{plantId}/weather-records`

3. `PUT /monitor/plants/{plantId}/weather-records/{recordId}`

4. `DELETE /monitor/plants/{plantId}/weather-records/{recordId}`

5. `POST /monitor/plants/{plantId}/weather-records:import`
- `multipart/form-data` 上传 Excel。

### 4.2.3 导入任务查询

1. `GET /monitor/import-jobs/{jobId}`
- 返回成功行数、失败行数、错误明细下载地址。

## 4.3 模型训练 API（forecast-service 编排）

1. `POST /forecast/model-train-jobs`
- Body:
  - `plantId`
  - `startDate`
  - `endDate`
  - `featureSetVersion`（可选）
  - `algorithm`（可选，默认 xgboost/lightgbm 策略）

2. `GET /forecast/model-train-jobs/{jobId}`

3. `GET /forecast/plants/{plantId}/models`
- 查询该电站模型版本列表（含生产/候选状态）。

4. `PUT /forecast/plants/{plantId}/models/{modelVersion}:promote`
- 将某模型版本提升为生产版本。

## 4.4 预测 API（forecast-service 编排）

1. `POST /forecast/jobs/dayahead`
- Body:
  - `plantId`
  - `forecastDate`
  - `modelVersion`（可选，不传则取该电站生产版本）
  - `weatherSource`（`api` 或 `manual`）

2. `GET /forecast/jobs/{jobId}`

3. `GET /forecast/plants/{plantId}/dayahead`
- Query: `forecastDate`, `modelVersion`（可选）

## 5. API 设计（内部）

## 5.1 forecast-service -> feature-service

1. `POST /internal/v1/features/training-dataset`
- 输入：`plantId`, `startTime`, `endTime`, `featureSetVersion`
- 输出：`datasetUri`, `featureVersion`, `sampleCount`

## 5.2 forecast-service -> model-service

1. `POST /internal/v1/models/train`
- 输入：`plantId`, `datasetUri`, `algorithm`, `featureVersion`
- 输出：`trainingJobId`

2. `GET /internal/v1/models/train/{trainingJobId}`
- 输出：训练状态、指标、`modelVersion`

## 5.3 forecast-service -> inference-service

1. `POST /internal/v1/inference/dayahead`
- 输入：`plantId`, `forecastDate`, `modelVersion`, `weatherSnapshot`
- 输出：96 点预测数组与摘要指标。

## 5.4 forecast-service -> weather adapter（可在 monitor-service 内）

1. `POST /internal/v1/weather/fetch-by-plant`
- 输入：`plantId`, `forecastDate`
- 输出：按 15 分钟对齐的天气快照。

## 6. 数据模型变更建议（PostgreSQL）

## 6.1 资源管理

1. `pp_plant`
- 电站主档，新增字段建议：
  - `plant_code`（唯一）
  - `latitude`, `longitude`
  - `capacity_kw`
  - `timezone`
  - `status`

2. `pp_user`, `pp_role`, `pp_permission`, `pp_user_role`, `pp_role_permission`
- 用户角色权限标准 RBAC。

## 6.2 采集数据

1. `pp_generation_record`
- `id`, `plant_id`, `device_id`, `ts`, `active_power_kw`, `reactive_power_kvar`, `apparent_power_kva`, `power_factor_total`, `power_factor_phase_a`, `power_factor_phase_b`, `power_factor_phase_c`, `voltage_phase_a`, `voltage_phase_b`, `voltage_phase_c`, `current_phase_a`, `current_phase_b`, `current_phase_c`, `frequency_hz`, `energy_15m_kwh`, `energy_daily_kwh`, `source`, `created_by`

2. `pp_weather_record`
- `id`, `plant_id`, `ts`, `ghi`, `temperature`, `humidity`, `cloud_cover`, `wind_speed`, `source`, `created_by`

3. `pp_import_job`
- `id`, `job_type`, `plant_id`, `status`, `file_uri`, `success_count`, `failed_count`, `error_report_uri`

## 6.3 训练与预测

1. `pp_model_registry`
- `id`, `plant_id`, `model_version`, `feature_version`, `algorithm`, `metrics_json`, `status`, `trained_at`

2. `pp_model_train_job`
- `id`, `plant_id`, `start_date`, `end_date`, `status`, `dataset_uri`, `model_version`, `error_message`

3. `pp_forecast_job`
- `id`, `plant_id`, `forecast_date`, `model_version`, `status`, `weather_source`, `error_message`

4. `pp_forecast_point`
- `id`, `forecast_job_id`, `plant_id`, `ts`, `predicted_power_kw`, `lower_bound_kw`, `upper_bound_kw`

## 7. 核心流程设计

## 7.1 Excel 导入流程

1. 前端上传 Excel 到导入接口。
2. monitor-service 创建导入任务并落库。
3. 解析校验（列名、时间格式、数值范围、重复键、三相参数完整性、功率因数范围）。
4. 写入业务表并记录错误行。
5. 返回任务状态与错误报告下载。

建议校验规则：

1. `powerFactorTotal` 范围建议为 `-1` 到 `1`。
2. 三相电压电流建议非负，异常值进入错误报告。
3. `energy15mKwh` 与功率时间积分口径做一致性校验（允许误差阈值）。
4. `energyDailyKwh` 必须按电站本地日历日累计。

## 7.2 模型训练流程

1. 前端提交训练任务（选择电站 + 日期范围）。
2. forecast-service 校验电站与权限。
3. 调用 feature-service 生成训练数据集。
4. 调用 model-service 训练并登记模型。
5. 回写训练任务状态与模型版本。
6. 用户可手动 promote 模型为生产版本。

## 7.3 预测流程

1. 前端提交预测请求（电站 + 日期）。
2. forecast-service 解析该电站生产模型版本。
3. 拉取天气数据：
- 若本地已有可用快照优先使用。
- 不足则调用天气 API 补采并落库。
4. 调用 inference-service 产出 96 点预测。
5. 落库并返回任务结果。

## 8. 鉴权与权限矩阵

1. 电站 CRUD：`plant:read`, `plant:write`
2. 用户/角色/权限：`system:admin`
3. 采集数据查看：`monitor:read`
4. 采集数据人工维护/导入：`monitor:write`
5. 训练任务：`model:train`
6. 模型发布：`model:publish`
7. 预测触发：`forecast:run`
8. 预测查询：`forecast:read`

## 9. 前后端联调约束

1. 所有接口遵循统一响应体与错误码。
2. 先落 OpenAPI，再开发前端调用。
3. Excel 导入模板文件要先固定版本（列头、单位、时区）。
4. 时间粒度统一 15 分钟，统一时区存储策略（建议 UTC 存储 + 本地时区展示）。
5. Excel 模板需区分必填列与可选列，发电采集模板至少包含：时间、电站、有功、无功、功率因数、三相电压、三相电流、15 分钟发电量、当日发电量。

## 10. Stage 5 实施顺序建议

1. 先做资源管理域（电站 + 用户/角色/权限）。
2. 再做采集数据域（发电量/天气 + Excel 导入）。
3. 再做训练任务域（按电站训练 + 模型版本管理）。
4. 最后做预测域（天气 API 拉取 + 按电站模型推理）。

## 11. 验收标准（本轮设计冻结）

1. 业务功能拆分清晰，服务边界明确。
2. 对外接口与内部接口有明确责任归属。
3. 数据库变更清单可支撑后续实现。
4. 可直接进入 API-first 开发与 OpenAPI 编写。

## 12. 本轮输出产物

1. monitor-service OpenAPI 草案：`backend/java/monitor-service/openapi.yaml`
2. Monitor Excel 模板规范：`docs/12-monitor-data-excel-template-spec.md`
