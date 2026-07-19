-- Stage 3: Hudi layer table definitions
-- Notes:
-- 1. Spark SQL / Hudi DDL skeletons.
-- 2. Partition columns are derived from ingestion or business timestamps.
-- 3. Table properties are declared for MoR/COW with primary keys and precombine fields.

-- ODS layer
CREATE TABLE IF NOT EXISTS ods_power_realtime (
  plant_id            STRING COMMENT '电站唯一标识',
  device_id           STRING COMMENT '设备唯一标识',
  ts                  TIMESTAMP COMMENT '采样时间',
  active_power_kw     DOUBLE COMMENT '有功功率（kW）',
  reactive_power_kvar DOUBLE COMMENT '无功功率（kVar）',
  voltage             DOUBLE COMMENT '电压值',
  current             DOUBLE COMMENT '电流值',
  status_code         STRING COMMENT '设备状态码',
  ingest_time         TIMESTAMP COMMENT '入湖时间',
  source_file         STRING COMMENT '来源文件路径',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,device_id,ts',
  preCombineField = 'ingest_time',
  type = 'mor'
);

CREATE TABLE IF NOT EXISTS ods_weather_forecast (
  plant_id            STRING COMMENT '电站唯一标识',
  forecast_time       TIMESTAMP COMMENT '天气预报时间',
  weather_code        STRING COMMENT '天气现象编码',
  temperature_c       DOUBLE COMMENT '温度（摄氏度）',
  humidity_pct        DOUBLE COMMENT '相对湿度（%）',
  wind_speed_mps      DOUBLE COMMENT '风速（m/s）',
  cloud_cover_pct     DOUBLE COMMENT '云量（%）',
  ingest_time         TIMESTAMP COMMENT '入湖时间',
  source_file         STRING COMMENT '来源文件路径',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,forecast_time',
  preCombineField = 'ingest_time',
  type = 'mor'
);

CREATE TABLE IF NOT EXISTS ods_device_status (
  plant_id            STRING COMMENT '电站唯一标识',
  device_id           STRING COMMENT '设备唯一标识',
  status_time         TIMESTAMP COMMENT '状态时间',
  status_code         STRING COMMENT '设备状态码',
  status_desc         STRING COMMENT '状态描述',
  ingest_time         TIMESTAMP COMMENT '入湖时间',
  source_file         STRING COMMENT '来源文件路径',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,device_id,status_time',
  preCombineField = 'ingest_time',
  type = 'mor'
);

-- DWD layer
CREATE TABLE IF NOT EXISTS dwd_power_15m (
  plant_id            STRING COMMENT '电站唯一标识',
  ts_15m              TIMESTAMP COMMENT '15 分钟对齐时间',
  active_power_kw     DOUBLE COMMENT '清洗后有功功率（kW）',
  is_missing_filled   BOOLEAN COMMENT '是否缺失值填补',
  is_anomaly          BOOLEAN COMMENT '是否异常样本',
  quality_score       INT COMMENT '数据质量评分',
  process_time        TIMESTAMP COMMENT '处理时间',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,ts_15m',
  preCombineField = 'process_time',
  type = 'mor'
);

CREATE TABLE IF NOT EXISTS dwd_weather_15m (
  plant_id            STRING COMMENT '电站唯一标识',
  ts_15m              TIMESTAMP COMMENT '15 分钟对齐时间',
  temperature_c       DOUBLE COMMENT '对齐后的温度（摄氏度）',
  humidity_pct        DOUBLE COMMENT '对齐后的湿度（%）',
  wind_speed_mps      DOUBLE COMMENT '对齐后的风速（m/s）',
  cloud_cover_pct     DOUBLE COMMENT '对齐后的云量（%）',
  process_time        TIMESTAMP COMMENT '处理时间',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,ts_15m',
  preCombineField = 'process_time',
  type = 'mor'
);

CREATE TABLE IF NOT EXISTS dwd_power_quality_tag (
  plant_id            STRING COMMENT '电站唯一标识',
  ts_15m              TIMESTAMP COMMENT '15 分钟对齐时间',
  tag_code            STRING COMMENT '质量标签编码',
  tag_desc            STRING COMMENT '质量标签描述',
  tag_source          STRING COMMENT '标签来源',
  process_time        TIMESTAMP COMMENT '处理时间',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,ts_15m,tag_code',
  preCombineField = 'process_time',
  type = 'cow'
);

-- DWS layer
CREATE TABLE IF NOT EXISTS dws_feature_snapshot (
  plant_id            STRING COMMENT '电站唯一标识',
  feature_time        TIMESTAMP COMMENT '特征对应时间点',
  feature_version     STRING COMMENT '特征版本号',
  dataset_role        STRING COMMENT '数据集角色（train/valid/test/infer）',
  feature_json        STRING COMMENT '特征内容 JSON',
  process_time        TIMESTAMP COMMENT '特征生成时间',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt, feature_version)
TBLPROPERTIES (
  primaryKey = 'plant_id,feature_time,feature_version',
  preCombineField = 'process_time',
  type = 'cow'
);

CREATE TABLE IF NOT EXISTS dws_monitor_realtime (
  plant_id            STRING COMMENT '电站唯一标识',
  snapshot_time       TIMESTAMP COMMENT '实时聚合快照时间',
  active_power_kw     DOUBLE COMMENT '当前有功功率（kW）',
  hourly_energy_kwh   DOUBLE COMMENT '小时累计电量（kWh）',
  device_online_rate  DOUBLE COMMENT '设备在线率（0-1）',
  process_time        TIMESTAMP COMMENT '处理时间',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,snapshot_time',
  preCombineField = 'process_time',
  type = 'mor'
);

-- ADS layer
CREATE TABLE IF NOT EXISTS ads_power_forecast_dayahead (
  plant_id            STRING COMMENT '电站唯一标识',
  forecast_run_time   TIMESTAMP COMMENT '预测批次运行时间',
  target_time         TIMESTAMP COMMENT '预测目标时刻',
  model_name          STRING COMMENT '模型名称',
  model_version       STRING COMMENT '模型版本',
  pred_power_kw       DOUBLE COMMENT '点预测功率（kW）',
  lower_bound_kw      DOUBLE COMMENT '预测下界（kW）',
  upper_bound_kw      DOUBLE COMMENT '预测上界（kW）',
  horizon_minutes     INT COMMENT '相对运行时刻的预测步长（分钟）',
  generate_time       TIMESTAMP COMMENT '结果生成时间',
  forecast_date       STRING COMMENT '预测日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (forecast_date)
TBLPROPERTIES (
  primaryKey = 'plant_id,forecast_run_time,target_time,model_version',
  preCombineField = 'generate_time',
  type = 'cow'
);

CREATE TABLE IF NOT EXISTS ads_forecast_evaluation_daily (
  plant_id            STRING COMMENT '电站唯一标识',
  forecast_date       DATE COMMENT '评估对应日期',
  model_name          STRING COMMENT '模型名称',
  model_version       STRING COMMENT '模型版本',
  rmse                DOUBLE COMMENT '均方根误差',
  mae                 DOUBLE COMMENT '平均绝对误差',
  mape                DOUBLE COMMENT '平均绝对百分比误差',
  r2                  DOUBLE COMMENT '决定系数',
  sample_count        BIGINT COMMENT '评估样本数量',
  generate_time       TIMESTAMP COMMENT '结果生成时间',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,forecast_date,model_version',
  preCombineField = 'generate_time',
  type = 'cow'
);

CREATE TABLE IF NOT EXISTS ads_monitor_dashboard_5m (
  plant_id            STRING COMMENT '电站唯一标识',
  snapshot_time       TIMESTAMP COMMENT '5 分钟监控快照时间',
  active_power_kw     DOUBLE COMMENT '当前有功功率（kW）',
  device_online_rate  DOUBLE COMMENT '设备在线率（0-1）',
  alarm_count         INT COMMENT '告警数量',
  generate_time       TIMESTAMP COMMENT '结果生成时间',
  dt                  STRING COMMENT '分区日期，格式 yyyy-MM-dd'
)
USING hudi
PARTITIONED BY (dt)
TBLPROPERTIES (
  primaryKey = 'plant_id,snapshot_time',
  preCombineField = 'generate_time',
  type = 'cow'
);
