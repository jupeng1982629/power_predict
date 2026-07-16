# 系统模块清单与服务边界说明

## 1. 文档目标

本文件定义光伏功率预测平台的模块清单、服务职责边界、跨服务调用关系与鉴权边界，作为后续数据库设计、API 契约和脚手架落地的统一约束。

## 2. 全局架构分层

平台采用 5 层逻辑分层：

1. 接入层：采集实时功率、天气预报、设备状态、外部文件导入。
2. 存储层：Hudi 数据湖 + PostgreSQL 业务库 + Redis 缓存 + MinIO 对象存储。
3. 计算层：Flink 实时处理 + Spark 离线特征/训练。
4. 服务层：Spring Boot 业务微服务 + FastAPI 算法微服务。
5. 展示层：Vue Web 门户与开放 API 调用方。

## 3. 微服务清单与职责

### 3.1 Java 微服务（平台域）

1. gateway-service
- 统一入口、路由转发、OAuth2 资源服务器校验、限流与审计。
- 不承载业务逻辑，只做网关策略与协议适配。

2. plant-service
- 电站主数据管理（电站、设备、额定容量、经纬度、并网信息）。
- 电站与数据源绑定关系管理。

3. monitor-service
- 实时功率与运行状态查询。
- 监测聚合指标输出（当前功率、小时累计、设备在线率）。

4. forecast-service
- 预测任务编排与状态查询。
- 调用 inference-service 生成日前 24h 预测。
- 查询预测结果与实际对比、误差分析。

5. system-service
- 用户、角色、权限、租户、字典、参数配置。
- 操作审计日志与系统配置中心。

### 3.2 Python 微服务（算法域）

1. feature-service
- 统一训练/推理特征口径。
- 从 Hudi DWD/DWS 层生成特征快照。

2. model-service
- 训练、评估、模型登记（MLflow）。
- 管理模型版本生命周期（candidate/production/rollback）。

3. inference-service
- 加载指定生产模型并执行日前 24h 预测。
- 返回 96 点预测值与元信息。

## 4. 服务边界与数据主责（Source of Truth）

1. 电站主数据（SoT）：plant-service + PostgreSQL。
2. 权限与账号（SoT）：system-service + PostgreSQL。
3. 实时监测结果（SoT）：monitor-service + Hudi ADS + Redis 热缓存。
4. 预测结果（SoT）：forecast-service + Hudi ADS。
5. 模型元数据（SoT）：model-service + MLflow + PostgreSQL 索引。

约束：任何服务不得越权修改非本域 SoT 数据，只能通过公开 API 或异步事件协作。

## 5. 服务交互边界

### 5.1 同步调用边界

1. gateway-service -> 各业务服务：HTTP/JSON。
2. forecast-service -> inference-service：HTTP/JSON，超时与重试受控。
3. forecast-service -> monitor-service：读取实时观测补充特征。
4. 各服务 -> system-service：权限与用户上下文查询（必要时本地缓存）。

### 5.2 异步事件边界

1. 数据接入事件：Kafka topic `raw-data-ingested`。
2. 特征生成完成事件：Kafka topic `feature-snapshot-ready`。
3. 预测任务完成事件：Kafka topic `forecast-job-finished`。
4. 告警事件：Kafka topic `platform-alert`。

## 6. 鉴权与安全边界

## 6.1 OAuth2 建议落地

推荐方案：

1. 授权服务器：Keycloak（开箱可用，支持 OAuth2/OIDC、RBAC、多租户 Realm）。
2. 网关层：Spring Cloud Gateway + Spring Security OAuth2 Resource Server。
3. 服务层：Spring Security OAuth2 Resource Server（JWT 验签 + Scope 校验）。

### 6.2 Token 与权限模型

1. 前端登录通过 OIDC Authorization Code + PKCE。
2. 网关校验 Access Token（JWT），并透传用户声明（sub、tenant_id、roles、scopes）。
3. 服务按 `scope + role + tenant_id` 进行细粒度鉴权。
4. 服务间调用采用 client_credentials，避免复用用户 Token。

### 6.3 边界约束

1. 外部流量必须经 gateway-service。
2. 内部服务禁止匿名访问，至少要求 mTLS 或 JWT 服务凭证。
3. 敏感接口（任务触发、模型发布、权限变更）必须写审计日志。

## 7. 模块到代码仓结构映射

```text
power_predict/
  backend/
    java/
      gateway-service/
      plant-service/
      monitor-service/
      forecast-service/
      system-service/
    python/
      feature-service/
      model-service/
      inference-service/
```

## 8. 非功能边界

1. 可用性：核心查询接口可用性 >= 99.9%。
2. 性能：实时监测查询 P95 < 300ms，日前预测查询 P95 < 500ms。
3. 一致性：跨域数据采用最终一致性，关键任务状态需幂等。
4. 可观测：所有服务必须输出结构化日志、指标与追踪 ID。

## 9. 迭代顺序建议

1. 先落地 gateway/system/plant 三个基础域。
2. 再接入 monitor + forecast 业务闭环。
3. 最后拆分/增强 feature/model/inference 算法域能力。
