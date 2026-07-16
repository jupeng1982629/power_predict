# Stage 2 Baseline: Local Infrastructure with Docker Compose

## Scope

This document records the baseline deliverables for Stage 2 local environment setup.

## Delivered Compose Files

Base compose:
- deploy/docker-compose/base/compose.infrastructure.yml
- deploy/docker-compose/base/compose.application.yml

Profiles:
- deploy/docker-compose/profiles/compose.local-min.yml
- deploy/docker-compose/profiles/compose.local-full.yml

Environment template:
- deploy/docker-compose/.env.local.example

## Local-Min Topology

Enabled components:
- postgres
- redis
- minio
- kafka
- mlflow

Intended use:
- fast local dependency bootstrapping for backend and algorithm development
- no Spark/Flink required

## Local-Full Topology

Enabled components:
- local-min components
- placeholder app containers (gateway, plant, monitor, forecast, system, feature, model, inference, web)
- optional compute containers (spark, flink-jobmanager, flink-taskmanager)

Intended use:
- full-chain integration rehearsal after service images are available

## Local Scripts

Added scripts under scripts/local:
- up-local-min.ps1
- up-local-full.ps1
- down-local.ps1
- README.md

Behavior:
- if deploy/docker-compose/.env.local does not exist, scripts copy it from .env.local.example
- startup and shutdown commands are standardized for team usage

## Notes and Constraints

- Compose files include service health checks for startup order reliability.
- Sensitive values are not stored in repository; only template values are committed.
- Application service images currently use placeholder image names for later replacement.

## Validation Status

- Static file creation completed.
- Runtime validation on this machine is blocked because Docker CLI is not available in the current terminal environment.

## Next Step

Proceed to Stage 3:
- create PostgreSQL schema DDL under sql/postgresql
- prepare Hudi SQL placeholders under sql/hudi
- add initialization script wiring for local environment
