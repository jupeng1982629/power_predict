# 项目脚手架与部署结构设计

## 1. 文档目标

本文件给出可直接初始化的项目脚手架目录、环境分层、Docker Compose 本地部署结构、Kubernetes 生产部署结构和 CI/CD 流程建议。

## 2. 仓库组织策略

建议采用单仓多模块（Monorepo），统一治理规范、接口契约、部署资产与文档。

## 3. 顶层目录结构

```text
power_predict/
  docs/
  backend/
    java/
      gateway-service/
      plant-service/
      monitor-service/
      forecast-service/
      system-service/
      common-libs/
    python/
      feature-service/
      model-service/
      inference-service/
      shared/
  frontend/
    web-portal/
  data/
    sample/
    contracts/
  sql/
    postgresql/
    hudi/
  deploy/
    docker-compose/
      base/
      profiles/
    kubernetes/
      base/
      overlays/
        dev/
        test/
        prod/
    helm/
  scripts/
    local/
    ci/
  .github/
    workflows/
```

## 4. Java 服务脚手架约束

每个 Java 服务建议结构：

```text
<service>/
  src/main/java/.../
    api/
    application/
    domain/
    infrastructure/
    security/
  src/main/resources/
    application.yml
  src/test/
  Dockerfile
  pom.xml
```

约束：

1. 安全配置集中在 `security` 包。
2. 外部调用统一走 `infrastructure/client`。
3. DTO 与领域对象分离，避免跨层污染。

## 5. Python 服务脚手架约束

每个 Python 服务建议结构：

```text
<service>/
  app/
    api/
    schemas/
    services/
    repositories/
    core/
  tests/
  migrations/
  Dockerfile
  pyproject.toml
```

约束：

1. API 层仅做参数校验与响应封装。
2. 特征与模型逻辑分别归属 `services/feature` 与 `services/model`。
3. 推理接口保持无状态，支持横向扩展。

## 6. 前端脚手架约束（Vue 3）

```text
frontend/web-portal/
  src/
    api/
    views/
    components/
    stores/
    router/
    utils/
    styles/
  public/
  Dockerfile
  package.json
```

约束：

1. API SDK 自动生成或统一封装。
2. 权限路由与按钮级权限按 Scope 控制。
3. 监测与预测图表组件独立可复用。

## 7. 配置与环境分层

建议环境：`local`、`dev`、`test`、`prod`。

配置策略：

1. 本地：`.env.local` + compose override。
2. 集群：ConfigMap + Secret。
3. 敏感信息不入仓，使用密钥管理（Key Vault/Vault/K8s Secret）。

## 8. Docker Compose 本地部署结构

## 8.1 组件分组

1. 基础设施：postgres、redis、minio、kafka、mlflow。
2. 计算组件：spark、flink（可选 profile）。
3. 应用组件：gateway、plant、monitor、forecast、system、feature、model、inference、web。

## 8.2 文件拆分建议

```text
deploy/docker-compose/
  base/
    compose.infrastructure.yml
    compose.application.yml
  profiles/
    compose.local-min.yml
    compose.local-full.yml
```

## 8.3 启动建议

1. 最小链路：`local-min`（不启 Spark/Flink）。
2. 全链路验证：`local-full`。
3. 保留健康检查与依赖启动顺序。

## 9. Kubernetes 部署结构

## 9.1 Base + Overlay

```text
deploy/kubernetes/
  base/
    gateway/
    plant/
    monitor/
    forecast/
    system/
    feature/
    model/
    inference/
    web/
    infra/
  overlays/
    dev/
    test/
    prod/
```

建议使用 Kustomize 管理多环境差异。

## 9.2 命名空间规划

1. `pv-platform-app`
2. `pv-platform-data`
3. `pv-platform-observability`

## 9.3 核心资源清单

1. Deployment/StatefulSet。
2. Service/Ingress。
3. ConfigMap/Secret。
4. HPA/PDB。
5. ServiceMonitor（Prometheus）。

## 10. CI/CD 结构设计

## 10.1 Pipeline 分层

1. PR Pipeline

- 代码检查、单测、接口契约校验、镜像构建不推送。

2. Main Pipeline

- 构建镜像、推送镜像仓库、生成部署清单。

3. Release Pipeline

- 环境部署、冒烟测试、回滚校验。

## 10.2 建议工作流文件

```text
.github/workflows/
  ci-java.yml
  ci-python.yml
  ci-frontend.yml
  contract-check.yml
  docker-release.yml
  deploy-dev.yml
  deploy-prod.yml
```

## 11. 可观测与运维集成

1. 日志：Loki/ELK，统一 traceId。
2. 指标：Prometheus + Grafana。
3. 链路追踪：OpenTelemetry + Jaeger/Tempo。
4. 告警：预测任务失败率、接口错误率、数据延迟。

## 12. 安全基线

1. 所有服务启用 OAuth2/JWT 资源服务器模式。
2. 网关统一 CORS、限流、WAF 与审计。
3. 服务镜像启用漏洞扫描与基础镜像最小化。
4. 关键配置项（数据库、对象存储、IdP）必须密钥化管理。

## 13. 初始化落地顺序

1. 初始化仓库目录与基础脚本。
2. 先搭本地 `local-min` 运行闭环。
3. 落地 API 契约和数据库迁移脚本。
4. 增加 Spark/Flink 与 Hudi 全链路。
5. 最后完善 K8s 与 CI/CD 发布流程。
