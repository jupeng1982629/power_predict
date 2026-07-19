-- Stage 4: local demo seed data for forecast MVP

BEGIN;

WITH day_context AS (
  SELECT date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') AS day_start
)
INSERT INTO mdm.plant_info (
  plant_id,
  plant_name,
  capacity_mw,
  latitude,
  longitude,
  elevation_m,
  tilt_angle,
  azimuth_angle,
  timezone,
  status
)
VALUES (
  'plant-demo-001',
  'Jiangsu Demo PV Plant',
  100.0000,
  31.230400,
  121.473700,
  8.50,
  25.00,
  180.00,
  'Asia/Shanghai',
  'active'
)
ON CONFLICT (plant_id) DO UPDATE
SET plant_name = EXCLUDED.plant_name,
    capacity_mw = EXCLUDED.capacity_mw,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    elevation_m = EXCLUDED.elevation_m,
    tilt_angle = EXCLUDED.tilt_angle,
    azimuth_angle = EXCLUDED.azimuth_angle,
    timezone = EXCLUDED.timezone,
    status = EXCLUDED.status,
    updated_at = now();

INSERT INTO iam.user_account (
  user_id,
  oidc_sub,
  username,
  display_name,
  tenant_id,
  enabled
)
VALUES (
  'user-demo-admin',
  'demo-admin-sub',
  'admin',
  'Demo Admin',
  'tenant-demo',
  true
)
ON CONFLICT (user_id) DO UPDATE
SET oidc_sub = EXCLUDED.oidc_sub,
    username = EXCLUDED.username,
    display_name = EXCLUDED.display_name,
    tenant_id = EXCLUDED.tenant_id,
    enabled = EXCLUDED.enabled,
    updated_at = now();

INSERT INTO model.model_registry_ref (
  model_name,
  model_version,
  feature_version,
  framework,
  metrics_json,
  artifact_uri,
  stage,
  train_start_time,
  train_end_time
)
VALUES (
  'pv-lightgbm-dayahead',
  '2026.07-demo',
  'feature-v1',
  'LightGBM',
  '{"rmse": 5.83, "mae": 3.94, "mape": 7.21, "r2": 0.962}'::jsonb,
  'minio://power-predict/models/pv-lightgbm-dayahead/2026.07-demo',
  'production',
  now() - interval '2 day',
  now() - interval '2 day' + interval '26 minute'
)
ON CONFLICT (model_name, model_version) DO UPDATE
SET feature_version = EXCLUDED.feature_version,
    framework = EXCLUDED.framework,
    metrics_json = EXCLUDED.metrics_json,
    artifact_uri = EXCLUDED.artifact_uri,
    stage = EXCLUDED.stage,
    train_start_time = EXCLUDED.train_start_time,
    train_end_time = EXCLUDED.train_end_time,
    updated_at = now();

WITH series AS (
  SELECT
    'plant-demo-001'::varchar(64) AS plant_id,
    (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') - (day_offset * interval '1 day')) AS day_start,
    day_offset,
    generate_series(0, 95) AS idx
  FROM generate_series(0, 13) AS day_offset
)
INSERT INTO ops.power_actual (
  plant_id,
  ts,
  active_power_kw,
  curtailment_flag,
  fault_flag,
  data_quality_flag
)
SELECT
  plant_id,
  (day_start + (idx * interval '15 minute')) AT TIME ZONE 'Asia/Shanghai',
  round(
    least(
      96000.0,
      greatest(
        0.0,
        sin(pi() * ((idx - 24)::numeric / 48.0)) * (88500.0 - day_offset * 110.0)
        + CASE WHEN idx BETWEEN 34 AND 58 THEN 4200.0 ELSE 0.0 END
      )
    )::numeric,
    2
  ),
  false,
  false,
  'good'
FROM series
ON CONFLICT (plant_id, ts) DO UPDATE
SET active_power_kw = EXCLUDED.active_power_kw,
    curtailment_flag = EXCLUDED.curtailment_flag,
    fault_flag = EXCLUDED.fault_flag,
    data_quality_flag = EXCLUDED.data_quality_flag,
    updated_at = now();

WITH run_context AS (
  SELECT
    'plant-demo-001'::varchar(64) AS plant_id,
    date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') AS day_start,
    (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') - interval '6 hour') AT TIME ZONE 'Asia/Shanghai' AS forecast_run_time
),
weather_points AS (
  SELECT
    plant_id,
    forecast_run_time,
    (day_start + (idx * interval '15 minute')) AT TIME ZONE 'Asia/Shanghai' AS target_time,
    round(greatest(0.0, sin(pi() * ((idx - 24)::numeric / 48.0)) * 910.0)::numeric, 2) AS ghi,
    round(greatest(0.0, sin(pi() * ((idx - 24)::numeric / 48.0)) * 720.0)::numeric, 2) AS dni,
    round(greatest(0.0, sin(pi() * ((idx - 24)::numeric / 48.0)) * 190.0)::numeric, 2) AS dhi,
    round((25 + sin(pi() * ((idx - 18)::numeric / 48.0)) * 7)::numeric, 2) AS temperature,
    round((62 - sin(pi() * ((idx - 18)::numeric / 48.0)) * 14)::numeric, 2) AS humidity,
    round((35 + cos(pi() * ((idx - 30)::numeric / 36.0)) * 18)::numeric, 2) AS cloud_cover,
    round((3.8 + sin(pi() * (idx::numeric / 24.0)) * 1.4)::numeric, 2) AS wind_speed,
    round((145 + sin(pi() * (idx::numeric / 18.0)) * 24)::numeric, 2) AS wind_direction,
    'demo-nwp'::varchar(32) AS source
  FROM run_context, generate_series(0, 95) AS idx
)
INSERT INTO ops.weather_forecast (
  plant_id,
  forecast_run_time,
  target_time,
  ghi,
  dni,
  dhi,
  temperature,
  humidity,
  cloud_cover,
  wind_speed,
  wind_direction,
  source
)
SELECT
  plant_id,
  forecast_run_time,
  target_time,
  ghi,
  dni,
  dhi,
  temperature,
  humidity,
  cloud_cover,
  wind_speed,
  wind_direction,
  source
FROM weather_points
ON CONFLICT (plant_id, forecast_run_time, target_time, source) DO UPDATE
SET ghi = EXCLUDED.ghi,
    dni = EXCLUDED.dni,
    dhi = EXCLUDED.dhi,
    temperature = EXCLUDED.temperature,
    humidity = EXCLUDED.humidity,
    cloud_cover = EXCLUDED.cloud_cover,
    wind_speed = EXCLUDED.wind_speed,
    wind_direction = EXCLUDED.wind_direction,
    updated_at = now();

WITH feature_points AS (
  SELECT
    'plant-demo-001'::varchar(64) AS plant_id,
    (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') + interval '12 hour') AT TIME ZONE 'Asia/Shanghai' AS feature_time,
    'feature-v1'::varchar(64) AS feature_version,
    '{"hour": 12, "ghi_roll_mean_4": 812.0, "power_lag_4": 76850.0, "clear_sky_ghi": 860.0}'::jsonb AS feature_payload,
    'prediction'::varchar(32) AS dataset_role
)
INSERT INTO model.feature_snapshot (
  plant_id,
  feature_time,
  feature_version,
  feature_payload,
  dataset_role
)
SELECT plant_id, feature_time, feature_version, feature_payload, dataset_role
FROM feature_points
ON CONFLICT (plant_id, feature_time, feature_version, dataset_role) DO UPDATE
SET feature_payload = EXCLUDED.feature_payload,
    updated_at = now();

WITH run_context AS (
  SELECT
    'plant-demo-001'::varchar(64) AS plant_id,
    date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') AS day_start,
    (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai') - interval '6 hour') AT TIME ZONE 'Asia/Shanghai' AS forecast_run_time
),
forecast_points AS (
  SELECT
    plant_id,
    forecast_run_time,
    (day_start + (idx * interval '15 minute')) AT TIME ZONE 'Asia/Shanghai' AS target_time,
    'pv-lightgbm-dayahead'::varchar(64) AS model_name,
    '2026.07-demo'::varchar(64) AS model_version,
    round(
      least(
        95000.0,
        greatest(
          0.0,
          sin(pi() * ((idx - 24)::numeric / 48.0)) * 87250.0
          + CASE WHEN idx BETWEEN 34 AND 58 THEN 3500.0 ELSE 0.0 END
        )
      )::numeric,
      2
    ) AS pred_power_kw,
    idx * 15 AS horizon_minutes
  FROM run_context, generate_series(0, 95) AS idx
)
INSERT INTO ops.power_forecast (
  plant_id,
  forecast_run_time,
  target_time,
  model_name,
  model_version,
  pred_power_kw,
  lower_bound_kw,
  upper_bound_kw,
  horizon_minutes
)
SELECT
  plant_id,
  forecast_run_time,
  target_time,
  model_name,
  model_version,
  pred_power_kw,
  round(greatest(0.0, pred_power_kw * 0.91), 2),
  round(pred_power_kw * 1.09, 2),
  horizon_minutes
FROM forecast_points
ON CONFLICT (plant_id, forecast_run_time, target_time, model_name, model_version) DO UPDATE
SET pred_power_kw = EXCLUDED.pred_power_kw,
    lower_bound_kw = EXCLUDED.lower_bound_kw,
    upper_bound_kw = EXCLUDED.upper_bound_kw,
    horizon_minutes = EXCLUDED.horizon_minutes,
    updated_at = now();

INSERT INTO ops.forecast_job (
  job_id,
  plant_id,
  forecast_date,
  trigger_type,
  status,
  model_name,
  model_version,
  requested_by,
  requested_at,
  started_at,
  finished_at,
  error_message
)
VALUES (
  'job-demo-001',
  'plant-demo-001',
  CURRENT_DATE,
  'manual',
  'success',
  'pv-lightgbm-dayahead',
  '2026.07-demo',
  'user-demo-admin',
  now() - interval '4 hour',
  now() - interval '4 hour' + interval '5 second',
  now() - interval '4 hour' + interval '12 second',
  null
)
ON CONFLICT (job_id) DO UPDATE
SET plant_id = EXCLUDED.plant_id,
    forecast_date = EXCLUDED.forecast_date,
    trigger_type = EXCLUDED.trigger_type,
    status = EXCLUDED.status,
    model_name = EXCLUDED.model_name,
    model_version = EXCLUDED.model_version,
    requested_by = EXCLUDED.requested_by,
    requested_at = EXCLUDED.requested_at,
    started_at = EXCLUDED.started_at,
    finished_at = EXCLUDED.finished_at,
    error_message = EXCLUDED.error_message,
    updated_at = now();

INSERT INTO ops.forecast_evaluation (
  plant_id,
  forecast_date,
  model_name,
  model_version,
  rmse,
  mae,
  mape,
  r2,
  sample_count
)
VALUES (
  'plant-demo-001',
  CURRENT_DATE,
  'pv-lightgbm-dayahead',
  '2026.07-demo',
  5.83,
  3.94,
  7.21,
  0.962,
  96
)
ON CONFLICT (plant_id, forecast_date, model_name, model_version) DO UPDATE
SET rmse = EXCLUDED.rmse,
    mae = EXCLUDED.mae,
    mape = EXCLUDED.mape,
    r2 = EXCLUDED.r2,
    sample_count = EXCLUDED.sample_count,
    updated_at = now();

COMMIT;