# Stage 3 Baseline: PostgreSQL Schema and Hudi Layer Tables

## Scope

This document records the deliverables created for Stage 3.

## PostgreSQL Deliverables

Location:

- sql/postgresql/001_init_schema.sql

Contents:

- schemas: mdm, iam, ops, model
- core tables:
  - mdm.plant_info
  - iam.user_account
  - ops.forecast_job
  - model.model_registry_ref
  - ops.audit_log
- indexes:
  - idx_forecast_job_status
  - idx_forecast_job_requested_at
  - idx_audit_log_created_at
  - idx_audit_log_actor_user_id
  - idx_plant_info_status
  - unique indexes for user_account constraints
- triggers:
  - updated_at auto-touch triggers for mutable tables

Idempotency approach:

- CREATE SCHEMA IF NOT EXISTS
- CREATE TABLE IF NOT EXISTS
- guarded DO blocks for indexes and triggers
- repeatable execution safe for re-runs

## Hudi Deliverables

Location:

- sql/hudi/001_create_layers.sql

Contents:

- ODS layer tables:
  - ods_power_realtime
  - ods_weather_forecast
  - ods_device_status
- DWD layer tables:
  - dwd_power_15m
  - dwd_weather_15m
  - dwd_power_quality_tag
- DWS layer tables:
  - dws_feature_snapshot
  - dws_monitor_realtime
- ADS layer tables:
  - ads_power_forecast_dayahead
  - ads_forecast_evaluation_daily
  - ads_monitor_dashboard_5m

Design notes:

- partition columns use derived date fields
- Hudi table properties include primaryKey, preCombineField, and table type
- fields are aligned with the platform data flow described in docs/02-database-and-hudi-schema-design.md

## Verification Status

- PostgreSQL DDL is written to be repeatable and constraint-aware.
- Hudi layer DDL skeletons are created for Spark/Hudi execution later.
- No commit was performed; changes remain under manual control as requested.

## Next Step

Proceed to Stage 4:

- add PostgreSQL seed data if needed for local testing
- add migration or execution instructions for PostgreSQL and Hudi scripts
- start service-side data access implementation
