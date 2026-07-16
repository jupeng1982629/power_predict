# Local Infra Scripts

PowerShell scripts for Stage 2 local environment.

## Scripts

- check-prereqs.ps1: verify Docker and Docker Compose are available
- bootstrap-infra.ps1: check prerequisites and start local infrastructure in one step
- up-local-min.ps1: start infrastructure only (postgres, redis, minio, kafka, mlflow)
- up-local-full.ps1: start infrastructure + application placeholders + compute placeholders
- down-local.ps1: stop and remove containers from the composed stack

## Usage (PowerShell)

From repository root:

1. `./scripts/local/check-prereqs.ps1`
2. `./scripts/local/bootstrap-infra.ps1 -Profile local-min`
3. `./scripts/local/bootstrap-infra.ps1 -Profile local-full`
4. `./scripts/local/down-local.ps1`

If `deploy/docker-compose/.env.local` does not exist, scripts auto-create it from `deploy/docker-compose/.env.local.example`.

Note: PostgreSQL, Redis, MinIO, Kafka, and MLflow are started as containers. They do not need separate native installation when Docker is available.
