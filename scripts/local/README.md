# Local Infra Scripts

PowerShell scripts for Stage 2 local environment.

## Scripts

- up-local-min.ps1: start infrastructure only (postgres, redis, minio, kafka, mlflow)
- up-local-full.ps1: start infrastructure + application placeholders + compute placeholders
- down-local.ps1: stop and remove containers from the composed stack

## Usage (PowerShell)

From repository root:

1. `./scripts/local/up-local-min.ps1`
2. `./scripts/local/up-local-full.ps1`
3. `./scripts/local/down-local.ps1`

If `deploy/docker-compose/.env.local` does not exist, scripts auto-create it from `deploy/docker-compose/.env.local.example`.
