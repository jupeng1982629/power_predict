# Local Infra Scripts

PowerShell scripts for Stage 2 local environment.

## Scripts

- init-postgres-schema.cmd: one-shot launcher to execute sql/postgresql/001_init_schema.sql inside the running PostgreSQL container
- init-postgres-schema.ps1: PowerShell implementation used by the launcher above
- init-postgres-seed.cmd: one-shot launcher to execute sql/postgresql/002_seed_demo_data.sql inside the running PostgreSQL container
- init-postgres-seed.ps1: PowerShell implementation used by the seed launcher above
- add-docker-path.ps1: add Docker Desktop bin directory to current session and user PATH
- setup-local-env.cmd: Windows launcher that bypasses PowerShell execution policy
- use-docker.ps1: add Docker Desktop bin directory to the current PowerShell session PATH
- setup-local-env.ps1: one-shot bootstrap for local infrastructure
- check-prereqs.ps1: verify Docker and Docker Compose are available
- bootstrap-infra.ps1: check prerequisites and start local infrastructure in one step
- up-local-min.ps1: start infrastructure only (postgres, redis, minio, kafka, mlflow)
- up-local-full.ps1: start infrastructure + application placeholders + compute placeholders
- down-local.ps1: stop and remove containers from the composed stack
- dev-stack.cmd: one-click launcher for full local dev stack lifecycle (start/stop/status)
- dev-stack.ps1: unified stack manager (dependency checks + infra/schema/seed + Java/Python/Web process management)

## Usage (PowerShell)

From repository root:

1. `./scripts/local/init-postgres-schema.cmd`
2. `./scripts/local/init-postgres-seed.cmd`
3. `./scripts/local/setup-local-env.cmd`
4. `./scripts/local/setup-local-env.ps1`
5. `./scripts/local/setup-local-env.ps1 -Profile local-full`
6. `./scripts/local/setup-local-env.ps1 -Status`
7. `./scripts/local/setup-local-env.ps1 -Stop`
8. `./scripts/local/dev-stack.cmd`
9. `./scripts/local/dev-stack.cmd -Action status`
10. `./scripts/local/dev-stack.cmd -Action stop`

## Unified Dev Stack (Recommended)

From repository root, use:

1. `./scripts/local/dev-stack.cmd`
2. `./scripts/local/dev-stack.cmd -Action status`
3. `./scripts/local/dev-stack.cmd -Action stop`

What `dev-stack` does on start:

- checks required commands and runtime dependencies (Docker, Maven, npm, Python packages)
- optionally brings up local infra via `setup-local-env.ps1`
- initializes PostgreSQL schema and seed data
- optionally builds Java modules (`common-libs`, `system-service`, `gateway-service`)
- starts and manages local processes:
  - system-service (8081)
  - gateway-service (8080)
  - model-service (8002)
  - inference-service (8003)
  - web-portal (5173)

Logs and managed process state are written under `.run/dev-stack/`.

Common options:

- `-SkipInfra`: don't start/stop Docker infra
- `-IncludeInfra`: when used with `-Action stop`, also stop Docker infra
- `-SkipSchemaInit`, `-SkipSeedInit`, `-SkipJavaBuild`
- `-SkipPythonInstall`, `-SkipWebInstall`
- `-InfraProfile local-min|local-full`

If you need to refresh the current PowerShell session PATH for Docker, run `./scripts/local/add-docker-path.ps1` in that same terminal before other commands.

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

Kafka mirror note:

- `setup-local-env.ps1` now tries fallback images automatically when `KAFKA_IMAGE` is unreachable and writes a working fallback back to `deploy/docker-compose/.env.local`.
- The default Kafka image was switched to `docker.m.daocloud.io/apache/kafka:3.8.0` for better accessibility in domestic networks.
