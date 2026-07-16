# Local Infra Scripts

PowerShell scripts for Stage 2 local environment.

## Scripts

- add-docker-path.ps1: add Docker Desktop bin directory to current session and user PATH
- setup-local-env.cmd: Windows launcher that bypasses PowerShell execution policy
- use-docker.ps1: add Docker Desktop bin directory to the current PowerShell session PATH
- setup-local-env.ps1: one-shot bootstrap for local infrastructure
- check-prereqs.ps1: verify Docker and Docker Compose are available
- bootstrap-infra.ps1: check prerequisites and start local infrastructure in one step
- up-local-min.ps1: start infrastructure only (postgres, redis, minio, kafka, mlflow)
- up-local-full.ps1: start infrastructure + application placeholders + compute placeholders
- down-local.ps1: stop and remove containers from the composed stack

## Usage (PowerShell)

From repository root:

1. `./scripts/local/add-docker-path.ps1`
2. `./scripts/local/setup-local-env.cmd`
3. `./scripts/local/setup-local-env.ps1`
4. `./scripts/local/setup-local-env.ps1 -Profile local-full`
5. `./scripts/local/setup-local-env.ps1 -Status`
6. `./scripts/local/setup-local-env.ps1 -Stop`

If `deploy/docker-compose/.env.local` does not exist, scripts auto-create it from `deploy/docker-compose/.env.local.example`.

Note: PostgreSQL, Redis, MinIO, Kafka, and MLflow are started as containers. They do not need separate native installation when Docker is available.

For `local-full`, the application images default to local tags such as `gateway-service:local` and are expected to be built locally in a later stage.

Optional helpers remain available for lower-level control if you need them.

If `docker` is still not recognized in the current terminal, run the helper above with dot-sourcing so it updates the current PowerShell session.

If the current terminal is an old session, close it and open a new PowerShell terminal in VS Code after running the helper or restarting VS Code.

If you want to fix the current terminal immediately without reopening it, run this in the same PowerShell window:

```powershell
$env:Path = 'C:\Program Files\Docker\Docker\resources\bin;' + $env:Path
docker version
```

This is the reliable way to update the active session PATH.

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
