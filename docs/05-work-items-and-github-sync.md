# 光伏预测平台工作事项记录与 GitHub 同步说明

## 1. 目标

本文档用于记录我们当前确定的实施步骤、阶段验收标准、执行顺序与 Git/GitHub 同步流程，确保后续开发与交付可追踪、可复盘。

## 2. 分阶段执行顺序（已确认）

### 阶段 1：仓库骨架与工程约束
- 建立 Monorepo 目录结构（backend/java、backend/python、frontend、deploy、sql、scripts）。
- 统一 Java/Python/Vue 基础工程约束。
- 验收标准：三端基础工程可启动，目录结构与设计文档一致。

### 阶段 2：本地基础设施可运行
- 使用 Docker Compose 启动 PostgreSQL、Redis、MinIO、Kafka 等基础组件。
- 验收标准：核心组件健康检查通过，可被服务访问。

### 阶段 3：数据库与数据层脚本
- 落地 PostgreSQL schema 与核心表。
- 准备 Hudi 分层表结构脚本。
- 验收标准：建表脚本可重复执行，索引与约束生效。

### 阶段 4：认证鉴权底座
- 接入 Keycloak（OIDC/OAuth2）。
- 网关与服务层完成 JWT 验签与基础权限控制。
- 准备最小 seed 数据，方便本地验证登录后页面与数据库联动。
- 验收标准：登录、鉴权、租户上下文透传可用。

### 阶段 5：API 契约先行
- 固化统一响应、错误码、版本与路由前缀规范。
- 输出 OpenAPI 文档并生成/封装前端 SDK。
- 验收标准：前后端可按契约联调。

### 阶段 6：业务服务按依赖顺序落地
- 顺序建议：plant-service -> system-service -> monitor-service -> forecast-service。
- inference-service 先提供 mock，再接真实模型。
- 验收标准：核心业务主链路可跑通。

### 阶段 7：算法链路闭环
- feature-service 最小特征集。
- model-service 训练与注册最小闭环。
- inference-service 接生产模型版本。
- 验收标准：可完成日前 24h（96 点）预测并入库。

### 阶段 8：前端页面分批接入
- 顺序建议：登录/权限 -> 电站管理 -> 实时监测 -> 预测任务与对比 -> 系统管理。
- 验收标准：主流程页面可演示、权限可控。

### 阶段 9：部署与 CI/CD
- 本地 Compose 稳定后，推进 Kubernetes/Helm 与流水线。
- 验收标准：具备 dev/test/prod 环境部署能力与回滚策略。

## 3. 每阶段共通交付物
- 设计与变更说明（docs）。
- 可执行脚本（sql、scripts、deploy）。
- 服务代码与测试用例。
- 里程碑提交记录（Git tags 可选）。

## 4. Git 与 GitHub 同步操作清单

## 4.1 本地初始化（首次）
1. 在项目根目录初始化 Git。
2. 配置默认分支为 main。
3. 创建 .gitignore。
4. 完成首个提交。

## 4.2 远程仓库创建
可选两种方式：
- 方式 A：通过 GitHub CLI 创建（需先登录 gh）。
- 方式 B：在 GitHub 网页手工创建空仓库后，复制远程地址。

## 4.3 关联远程并推送
1. 添加 origin。
2. 推送 main 到远端并建立上游跟踪。

## 5. 建议分支策略
- main：稳定主干。
- develop：日常集成（可选）。
- feature/<模块名>：功能开发分支。
- hotfix/<问题编号>：线上修复。

## 6. 提交规范建议
- feat: 新功能
- fix: 缺陷修复
- docs: 文档更新
- refactor: 重构
- test: 测试相关
- chore: 构建或工具链调整

## 7. 当前状态
- 已完成：阶段顺序确认。
- 已完成：本文件建立并记录实施流程。
- 待执行：初始化 Git、创建 GitHub 远程仓库、首推 main。
- 待执行：阶段 4 的本地种子数据与认证底座接入。
