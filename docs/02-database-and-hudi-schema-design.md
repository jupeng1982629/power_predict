# 数据库与 Hudi 表结构设计

## 1. 文档目标

本文件定义平台 PostgreSQL 事务库与 Hudi 湖仓表的结构、分层、主键策略、分区策略和写入约束，确保数据模型可直接支撑多电站实时监测与日前预测业务。

## 2. 数据存储职责分工

1. PostgreSQL：主数据、权限数据、任务状态、配置与审计。
2. Hudi：时序明细、清洗明细、主题宽表、预测结果与监测聚合。
3. Redis：热点查询缓存与会话短期缓存。
4. MinIO：原始文件、模型工件、导出报表。

## 3. PostgreSQL 逻辑库划分

建议 schema：

1. `mdm`：主数据域。
2. `iam`：账号与权限域。
3. `ops`：任务编排、调度、审计域。
4. `model`：模型索引与发布记录。

## 4. PostgreSQL 核心表

## 4.1 `mdm.plant_info`

用途：电站主数据。

```sql
create table if not exists mdm.plant_info (
  plant_id            varchar(64) primary key,                        -- 电站唯一标识
  plant_name          varchar(128) not null,                          -- 电站名称
  capacity_mw         numeric(12,4) not null,                         -- 装机容量（MW）
  latitude            numeric(10,6) not null,                         -- 纬度
  longitude           numeric(10,6) not null,                         -- 经度
  elevation_m         numeric(10,2),                                  -- 海拔高度（米）
  tilt_angle          numeric(6,2),                                   -- 组件倾角（度）
  azimuth_angle       numeric(6,2),                                   -- 组件方位角（度）
  timezone            varchar(64) not null default 'Asia/Shanghai',   -- 时区
  status              varchar(16) not null default 'active',          -- 状态（active/inactive）
  created_at          timestamptz not null default now(),             -- 创建时间
  updated_at          timestamptz not null default now()              -- 更新时间
);
```

## 4.2 `iam.user_account`

用途：平台用户账户（外部 IdP 账号映射）。

```sql
create table if not exists iam.user_account (
  user_id             varchar(64) primary key,             -- 用户唯一标识
  oidc_sub            varchar(128) not null unique,        -- OIDC 主题标识（来自 IdP）
  username            varchar(64) not null unique,         -- 登录用户名
  display_name        varchar(64),                         -- 显示名称
  tenant_id           varchar(64) not null,                -- 租户标识
  enabled             boolean not null default true,       -- 启用状态
  created_at          timestamptz not null default now(),  -- 创建时间
  updated_at          timestamptz not null default now()   -- 更新时间
);
```

## 4.3 `ops.forecast_job`

用途：预测任务状态追踪。

```sql
create table if not exists ops.forecast_job (
  job_id              varchar(64) primary key,  -- 任务唯一标识
  plant_id            varchar(64) not null,     -- 电站 ID
  forecast_date       date not null,            -- 预测目标日期
  trigger_type        varchar(16) not null,     -- 触发类型（manual/schedule）
  status              varchar(16) not null,     -- 任务状态（pending/running/success/failed）
  model_name          varchar(64),              -- 模型名称
  model_version       varchar(64),              -- 模型版本
  requested_by        varchar(64),              -- 任务触发人
  requested_at        timestamptz not null,     -- 请求时间
  started_at          timestamptz,              -- 开始执行时间
  finished_at         timestamptz,              -- 结束执行时间
  error_message       text,                     -- 失败错误信息
  unique (plant_id, forecast_date, trigger_type)
);
create index if not exists idx_forecast_job_status on ops.forecast_job(status);
```

## 4.4 `model.model_registry_ref`

用途：模型注册索引与上线状态。

```sql
create table if not exists model.model_registry_ref (
  model_name          varchar(64) not null,               -- 模型名称
  model_version       varchar(64) not null,               -- 模型版本
  feature_version     varchar(64) not null,               -- 对应特征版本
  framework           varchar(32) not null,               -- 训练框架（xgboost/lightgbm 等）
  metrics_json        jsonb not null,                     -- 评估指标 JSON
  artifact_uri        varchar(512) not null,              -- 模型工件存储路径
  stage               varchar(16) not null,               -- 阶段（candidate/production/archived）
  train_start_time    timestamptz,                        -- 训练开始时间
  train_end_time      timestamptz,                        -- 训练结束时间
  created_at          timestamptz not null default now(), -- 记录创建时间
  primary key (model_name, model_version)
);
```

## 4.5 `ops.audit_log`

用途：敏感操作审计。

```sql
create table if not exists ops.audit_log (
  audit_id            bigserial primary key,              -- 审计流水号
  actor_user_id       varchar(64),                        -- 操作用户 ID
  actor_client_id     varchar(64),                        -- 调用方客户端 ID
  action              varchar(64) not null,               -- 操作动作
  resource_type       varchar(64) not null,               -- 资源类型
  resource_id         varchar(128),                       -- 资源 ID
  result              varchar(16) not null,               -- 操作结果（success/failed）
  trace_id            varchar(64),                        -- 链路追踪 ID
  detail_json         jsonb,                              -- 详细上下文 JSON
  created_at          timestamptz not null default now()  -- 发生时间
);
create index if not exists idx_audit_log_created_at on ops.audit_log(created_at);
```

## 5. Hudi 分层与表清单

## 5.1 ODS 层

1. `ods_power_realtime`
2. `ods_weather_forecast`
3. `ods_device_status`

## 5.2 DWD 层

1. `dwd_power_15m`
2. `dwd_weather_15m`
3. `dwd_power_quality_tag`

## 5.3 DWS 层

1. `dws_feature_snapshot`
2. `dws_monitor_realtime`

## 5.4 ADS 层

1. `ads_power_forecast_dayahead`
2. `ads_forecast_evaluation_daily`
3. `ads_monitor_dashboard_5m`

## 6. Hudi 核心表结构

说明：以下以 Spark SQL 风格描述字段。

## 6.1 `ods_power_realtime`

```sql
create table ods_power_realtime (
  plant_id            string comment '电站唯一标识',
  device_id           string comment '设备唯一标识',
  ts                  timestamp comment '采样时间',
  active_power_kw     double comment '有功功率（kW）',
  reactive_power_kvar double comment '无功功率（kVar）',
  voltage             double comment '电压值',
  current             double comment '电流值',
  status_code         string comment '设备状态码',
  ingest_time         timestamp comment '入湖时间',
  source_file         string comment '来源文件路径'
)
using hudi
partitioned by (dt)
tblproperties (
  primaryKey = 'plant_id,device_id,ts',
  preCombineField = 'ingest_time',
  type = 'mor'
);
```

分区字段建议：`dt=yyyy-MM-dd`（由 ts 派生）。

## 6.2 `dwd_power_15m`

```sql
create table dwd_power_15m (
  plant_id            string comment '电站唯一标识',
  ts_15m              timestamp comment '15 分钟对齐时间',
  active_power_kw     double comment '清洗后有功功率（kW）',
  is_missing_filled   boolean comment '是否缺失值填补',
  is_anomaly          boolean comment '是否异常样本',
  quality_score       int comment '数据质量评分',
  process_time        timestamp comment '处理时间'
)
using hudi
partitioned by (dt)
tblproperties (
  primaryKey = 'plant_id,ts_15m',
  preCombineField = 'process_time',
  type = 'mor'
);
```

## 6.3 `dws_feature_snapshot`

```sql
create table dws_feature_snapshot (
  plant_id            string comment '电站唯一标识',
  feature_time        timestamp comment '特征对应时间点',
  feature_version     string comment '特征版本号',
  dataset_role        string comment '数据集角色（train/valid/test/infer）',
  feature_json        string comment '特征内容 JSON',
  process_time        timestamp comment '特征生成时间'
)
using hudi
partitioned by (dt, feature_version)
tblproperties (
  primaryKey = 'plant_id,feature_time,feature_version',
  preCombineField = 'process_time',
  type = 'cow'
);
```

## 6.4 `ads_power_forecast_dayahead`

```sql
create table ads_power_forecast_dayahead (
  plant_id            string comment '电站唯一标识',
  forecast_run_time   timestamp comment '预测批次运行时间',
  target_time         timestamp comment '预测目标时刻',
  model_name          string comment '模型名称',
  model_version       string comment '模型版本',
  pred_power_kw       double comment '点预测功率（kW）',
  lower_bound_kw      double comment '预测下界（kW）',
  upper_bound_kw      double comment '预测上界（kW）',
  horizon_minutes     int comment '相对运行时刻的预测步长（分钟）',
  generate_time       timestamp comment '结果生成时间'
)
using hudi
partitioned by (forecast_date)
tblproperties (
  primaryKey = 'plant_id,forecast_run_time,target_time,model_version',
  preCombineField = 'generate_time',
  type = 'cow'
);
```

`forecast_date` 由 `target_time` 派生，格式 `yyyy-MM-dd`。

## 6.5 `ads_forecast_evaluation_daily`

```sql
create table ads_forecast_evaluation_daily (
  plant_id            string comment '电站唯一标识',
  forecast_date       date comment '评估对应日期',
  model_name          string comment '模型名称',
  model_version       string comment '模型版本',
  rmse                double comment '均方根误差',
  mae                 double comment '平均绝对误差',
  mape                double comment '平均绝对百分比误差',
  r2                  double comment '决定系数',
  sample_count        bigint comment '评估样本数量',
  calculate_time      timestamp comment '评估计算时间'
)
using hudi
partitioned by (forecast_date)
tblproperties (
  primaryKey = 'plant_id,forecast_date,model_name,model_version',
  preCombineField = 'calculate_time',
  type = 'cow'
);
```

## 7. 索引、分区与性能建议

1. PostgreSQL 对 `plant_id + 时间` 建联合索引。
2. Hudi 分区以日期为主，避免过细分区导致小文件问题。
3. 预测结果查询按 `plant_id + forecast_date` 组织二级索引或 metadata table。
4. 实时监测热数据通过 Redis 缓存，TTL 建议 30-120 秒。

## 8. 数据质量与幂等约束

1. Hudi 主键必须稳定且不包含可变业务字段。
2. 重复写入依赖 `primaryKey + preCombineField` 去重。
3. 数据缺失和异常标记字段不得在下游删除，保证可追溯。
4. 训练与推理使用同一 `feature_version`，避免口径漂移。

## 9. 生命周期管理

1. ODS 保留 180 天。
2. DWD/DWS 保留 365 天。
3. ADS 预测与评估保留 730 天。
4. 超期分区归档到冷存储（MinIO/S3 归档桶）。

## 10. 权限控制

1. PostgreSQL 按 schema 分权：`app_rw`、`app_ro`、`ops_admin`。
2. Hudi 表按层授权：算法域可写 DWD/DWS，平台域只读 DWD、读写 ADS。
3. 敏感数据访问必须经服务层，不直接暴露数据库账号给前端。
