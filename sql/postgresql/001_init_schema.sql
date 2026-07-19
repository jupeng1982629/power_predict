-- Stage 3 and 4: PostgreSQL schema, forecast domain tables, and core indexes

BEGIN;

CREATE SCHEMA IF NOT EXISTS mdm;
CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS ops;
CREATE SCHEMA IF NOT EXISTS model;

CREATE OR REPLACE FUNCTION ops.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TABLE IF NOT EXISTS mdm.plant_info (
  plant_id            varchar(64) PRIMARY KEY,
  plant_name          varchar(128) NOT NULL,
  capacity_mw         numeric(12,4) NOT NULL,
  latitude            numeric(10,6) NOT NULL,
  longitude           numeric(10,6) NOT NULL,
  elevation_m         numeric(10,2),
  tilt_angle          numeric(6,2),
  azimuth_angle       numeric(6,2),
  timezone            varchar(64) NOT NULL DEFAULT 'Asia/Shanghai',
  status              varchar(16) NOT NULL DEFAULT 'active',
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_plant_info_status CHECK (status IN ('active', 'inactive')),
  CONSTRAINT ck_plant_info_capacity_mw CHECK (capacity_mw >= 0),
  CONSTRAINT ck_plant_info_latitude CHECK (latitude BETWEEN -90 AND 90),
  CONSTRAINT ck_plant_info_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE IF NOT EXISTS iam.user_account (
  user_id             varchar(64) PRIMARY KEY,
  oidc_sub            varchar(128) NOT NULL,
  username            varchar(64) NOT NULL,
  display_name        varchar(64),
  tenant_id           varchar(64) NOT NULL,
  enabled             boolean NOT NULL DEFAULT true,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_user_account_username_not_blank CHECK (btrim(username) <> ''),
  CONSTRAINT ck_user_account_tenant_not_blank CHECK (btrim(tenant_id) <> '')
);

CREATE TABLE IF NOT EXISTS ops.forecast_job (
  job_id              varchar(64) PRIMARY KEY,
  plant_id            varchar(64) NOT NULL,
  forecast_date       date NOT NULL,
  trigger_type        varchar(16) NOT NULL,
  status              varchar(16) NOT NULL,
  model_name          varchar(64),
  model_version       varchar(64),
  requested_by        varchar(64),
  requested_at        timestamptz NOT NULL DEFAULT now(),
  started_at          timestamptz,
  finished_at         timestamptz,
  error_message       text,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_forecast_job_trigger_type CHECK (trigger_type IN ('manual', 'schedule')),
  CONSTRAINT ck_forecast_job_status CHECK (status IN ('pending', 'running', 'success', 'failed')),
  CONSTRAINT ck_forecast_job_forecast_date CHECK (forecast_date >= DATE '2000-01-01'),
  CONSTRAINT uq_forecast_job_plant_date_trigger UNIQUE (plant_id, forecast_date, trigger_type)
);

CREATE TABLE IF NOT EXISTS ops.power_actual (
  plant_id            varchar(64) NOT NULL,
  ts                  timestamptz NOT NULL,
  active_power_kw     numeric(12,2) NOT NULL,
  curtailment_flag    boolean NOT NULL DEFAULT false,
  fault_flag          boolean NOT NULL DEFAULT false,
  data_quality_flag   varchar(16) NOT NULL DEFAULT 'good',
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_power_actual PRIMARY KEY (plant_id, ts),
  CONSTRAINT ck_power_actual_non_negative CHECK (active_power_kw >= 0)
);

CREATE TABLE IF NOT EXISTS ops.weather_forecast (
  plant_id            varchar(64) NOT NULL,
  forecast_run_time   timestamptz NOT NULL,
  target_time         timestamptz NOT NULL,
  ghi                 numeric(12,2) NOT NULL DEFAULT 0,
  dni                 numeric(12,2) NOT NULL DEFAULT 0,
  dhi                 numeric(12,2) NOT NULL DEFAULT 0,
  temperature         numeric(10,2),
  humidity            numeric(10,2),
  cloud_cover         numeric(10,2),
  wind_speed          numeric(10,2),
  wind_direction      numeric(10,2),
  source              varchar(32) NOT NULL DEFAULT 'demo-nwp',
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_weather_forecast PRIMARY KEY (plant_id, forecast_run_time, target_time, source)
);

CREATE TABLE IF NOT EXISTS model.feature_snapshot (
  plant_id            varchar(64) NOT NULL,
  feature_time        timestamptz NOT NULL,
  feature_version     varchar(64) NOT NULL,
  feature_payload     jsonb NOT NULL,
  dataset_role        varchar(32) NOT NULL,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_feature_snapshot PRIMARY KEY (plant_id, feature_time, feature_version, dataset_role)
);

CREATE TABLE IF NOT EXISTS model.model_registry_ref (
  model_name          varchar(64) NOT NULL,
  model_version       varchar(64) NOT NULL,
  feature_version     varchar(64) NOT NULL,
  framework           varchar(32) NOT NULL,
  metrics_json        jsonb NOT NULL,
  artifact_uri        varchar(512) NOT NULL,
  stage               varchar(16) NOT NULL,
  train_start_time    timestamptz,
  train_end_time      timestamptz,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_model_registry_ref PRIMARY KEY (model_name, model_version),
  CONSTRAINT ck_model_registry_stage CHECK (stage IN ('candidate', 'production', 'archived')),
  CONSTRAINT ck_model_registry_framework CHECK (btrim(framework) <> '')
);

CREATE TABLE IF NOT EXISTS ops.power_forecast (
  plant_id            varchar(64) NOT NULL,
  forecast_run_time   timestamptz NOT NULL,
  target_time         timestamptz NOT NULL,
  model_name          varchar(64) NOT NULL,
  model_version       varchar(64) NOT NULL,
  pred_power_kw       numeric(12,2) NOT NULL,
  lower_bound_kw      numeric(12,2),
  upper_bound_kw      numeric(12,2),
  horizon_minutes     integer NOT NULL,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_power_forecast PRIMARY KEY (plant_id, forecast_run_time, target_time, model_name, model_version),
  CONSTRAINT ck_power_forecast_non_negative CHECK (pred_power_kw >= 0),
  CONSTRAINT ck_power_forecast_horizon_positive CHECK (horizon_minutes >= 0)
);

CREATE TABLE IF NOT EXISTS ops.forecast_evaluation (
  plant_id            varchar(64) NOT NULL,
  forecast_date       date NOT NULL,
  model_name          varchar(64) NOT NULL,
  model_version       varchar(64) NOT NULL,
  rmse                numeric(12,4) NOT NULL,
  mae                 numeric(12,4) NOT NULL,
  mape                numeric(12,4),
  r2                  numeric(12,4),
  sample_count        integer NOT NULL,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_forecast_evaluation PRIMARY KEY (plant_id, forecast_date, model_name, model_version)
);

CREATE TABLE IF NOT EXISTS ops.audit_log (
  audit_id            bigserial PRIMARY KEY,
  actor_user_id       varchar(64),
  actor_client_id     varchar(64),
  action              varchar(64) NOT NULL,
  resource_type       varchar(64) NOT NULL,
  resource_id         varchar(128),
  result              varchar(16) NOT NULL,
  trace_id            varchar(64),
  detail_json         jsonb,
  created_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_audit_log_result CHECK (result IN ('success', 'failed')),
  CONSTRAINT ck_audit_log_action_not_blank CHECK (btrim(action) <> ''),
  CONSTRAINT ck_audit_log_resource_type_not_blank CHECK (btrim(resource_type) <> '')
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE con.conname = 'fk_forecast_job_plant_info'
      AND nsp.nspname = 'ops'
  ) THEN
    EXECUTE 'ALTER TABLE ops.forecast_job '
      || 'ADD CONSTRAINT fk_forecast_job_plant_info '
      || 'FOREIGN KEY (plant_id) REFERENCES mdm.plant_info(plant_id) '
      || 'ON UPDATE CASCADE '
      || 'ON DELETE RESTRICT';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE con.conname = 'fk_power_actual_plant_info'
      AND nsp.nspname = 'ops'
  ) THEN
    EXECUTE 'ALTER TABLE ops.power_actual '
      || 'ADD CONSTRAINT fk_power_actual_plant_info '
      || 'FOREIGN KEY (plant_id) REFERENCES mdm.plant_info(plant_id) '
      || 'ON UPDATE CASCADE ON DELETE RESTRICT';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE con.conname = 'fk_weather_forecast_plant_info'
      AND nsp.nspname = 'ops'
  ) THEN
    EXECUTE 'ALTER TABLE ops.weather_forecast '
      || 'ADD CONSTRAINT fk_weather_forecast_plant_info '
      || 'FOREIGN KEY (plant_id) REFERENCES mdm.plant_info(plant_id) '
      || 'ON UPDATE CASCADE ON DELETE RESTRICT';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE con.conname = 'fk_feature_snapshot_plant_info'
      AND nsp.nspname = 'model'
  ) THEN
    EXECUTE 'ALTER TABLE model.feature_snapshot '
      || 'ADD CONSTRAINT fk_feature_snapshot_plant_info '
      || 'FOREIGN KEY (plant_id) REFERENCES mdm.plant_info(plant_id) '
      || 'ON UPDATE CASCADE ON DELETE RESTRICT';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE con.conname = 'fk_power_forecast_plant_info'
      AND nsp.nspname = 'ops'
  ) THEN
    EXECUTE 'ALTER TABLE ops.power_forecast '
      || 'ADD CONSTRAINT fk_power_forecast_plant_info '
      || 'FOREIGN KEY (plant_id) REFERENCES mdm.plant_info(plant_id) '
      || 'ON UPDATE CASCADE ON DELETE RESTRICT';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE con.conname = 'fk_forecast_evaluation_plant_info'
      AND nsp.nspname = 'ops'
  ) THEN
    EXECUTE 'ALTER TABLE ops.forecast_evaluation '
      || 'ADD CONSTRAINT fk_forecast_evaluation_plant_info '
      || 'FOREIGN KEY (plant_id) REFERENCES mdm.plant_info(plant_id) '
      || 'ON UPDATE CASCADE ON DELETE RESTRICT';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_forecast_job_status' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE INDEX idx_forecast_job_status ON ops.forecast_job(status)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_forecast_job_requested_at' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE INDEX idx_forecast_job_requested_at ON ops.forecast_job(requested_at DESC)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_power_actual_ts' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE INDEX idx_power_actual_ts ON ops.power_actual(ts DESC)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_weather_forecast_target_time' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE INDEX idx_weather_forecast_target_time ON ops.weather_forecast(target_time DESC)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_power_forecast_target_time' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE INDEX idx_power_forecast_target_time ON ops.power_forecast(target_time DESC)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_forecast_evaluation_forecast_date' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE INDEX idx_forecast_evaluation_forecast_date ON ops.forecast_evaluation(forecast_date DESC)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_audit_log_created_at' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE INDEX idx_audit_log_created_at ON ops.audit_log(created_at DESC)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_audit_log_actor_user_id' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE INDEX idx_audit_log_actor_user_id ON ops.audit_log(actor_user_id)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'uq_user_account_oidc_sub' AND n.nspname = 'iam'
  ) THEN
    EXECUTE 'CREATE UNIQUE INDEX uq_user_account_oidc_sub ON iam.user_account(oidc_sub)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'uq_user_account_username' AND n.nspname = 'iam'
  ) THEN
    EXECUTE 'CREATE UNIQUE INDEX uq_user_account_username ON iam.user_account(username)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'idx_plant_info_status' AND n.nspname = 'mdm'
  ) THEN
    EXECUTE 'CREATE INDEX idx_plant_info_status ON mdm.plant_info(status)';
  END IF;
END $$;

CREATE OR REPLACE FUNCTION ops.touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_plant_info_updated_at' AND n.nspname = 'mdm'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_plant_info_updated_at BEFORE UPDATE ON mdm.plant_info FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_user_account_updated_at' AND n.nspname = 'iam'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_user_account_updated_at BEFORE UPDATE ON iam.user_account FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_forecast_job_updated_at' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_forecast_job_updated_at BEFORE UPDATE ON ops.forecast_job FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_power_actual_updated_at' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_power_actual_updated_at BEFORE UPDATE ON ops.power_actual FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_weather_forecast_updated_at' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_weather_forecast_updated_at BEFORE UPDATE ON ops.weather_forecast FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_feature_snapshot_updated_at' AND n.nspname = 'model'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_feature_snapshot_updated_at BEFORE UPDATE ON model.feature_snapshot FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_model_registry_ref_updated_at' AND n.nspname = 'model'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_model_registry_ref_updated_at BEFORE UPDATE ON model.model_registry_ref FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_power_forecast_updated_at' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_power_forecast_updated_at BEFORE UPDATE ON ops.power_forecast FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE t.tgname = 'trg_forecast_evaluation_updated_at' AND n.nspname = 'ops'
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_forecast_evaluation_updated_at BEFORE UPDATE ON ops.forecast_evaluation FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at()';
  END IF;
END $$;

COMMIT;
