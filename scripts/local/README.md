# Local Infra Scripts

PowerShell scripts for Stage 2 local environment.

## Scripts

- setup-local-env.ps1: one-shot bootstrap for local infrastructure
- check-prereqs.ps1: verify Docker and Docker Compose are available
- bootstrap-infra.ps1: check prerequisites and start local infrastructure in one step
- up-local-min.ps1: start infrastructure only (postgres, redis, minio, kafka, mlflow)
- up-local-full.ps1: start infrastructure + application placeholders + compute placeholders
- down-local.ps1: stop and remove containers from the composed stack

## Usage (PowerShell)

From repository root:

1. `./scripts/local/setup-local-env.ps1`
2. `./scripts/local/setup-local-env.ps1 -Profile local-full`
3. `./scripts/local/setup-local-env.ps1 -Status`
4. `./scripts/local/setup-local-env.ps1 -Stop`

If `deploy/docker-compose/.env.local` does not exist, scripts auto-create it from `deploy/docker-compose/.env.local.example`.

Note: PostgreSQL, Redis, MinIO, Kafka, and MLflow are started as containers. They do not need separate native installation when Docker is available.

Optional helpers remain available for lower-level control if you need them.

## Registry/Mirror Overrides

If image pulls fail because Docker Hub or GHCR is not reachable, update `deploy/docker-compose/.env.local` and set these variables to a reachable mirror or private registry:

- POSTGRES_IMAGE
- REDIS_IMAGE
- MINIO_IMAGE
- KAFKA_IMAGE
- MLFLOW_IMAGE

Example:

```text
POSTGRES_IMAGE=registry.example.com/library/postgres:16-alpine
REDIS_IMAGE=registry.example.com/library/redis:7-alpine
```
