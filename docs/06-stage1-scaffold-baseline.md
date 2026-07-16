# Stage 1 Baseline: Repository Scaffold and Engineering Constraints

## Scope

This document records the completed baseline work for Stage 1.

## Completed Structure

The repository scaffold has been created with these top-level directories:
- backend/
- frontend/
- data/
- sql/
- deploy/
- scripts/
- .github/workflows/

## Backend Baseline

Java workspace created:
- parent pom: backend/java/pom.xml
- modules: gateway-service, plant-service, monitor-service, forecast-service, system-service, common-libs
- each service has:
  - pom.xml
  - src/main/resources/application.yml
  - src/main/java/.gitkeep
  - src/test/java/.gitkeep
  - README.md

Python workspace created:
- services: feature-service, model-service, inference-service
- each service has:
  - pyproject.toml
  - app/main.py (minimal /health endpoint)
  - tests/.gitkeep
  - migrations/.gitkeep
  - README.md
- shared module: backend/python/shared/README.md

## Frontend Baseline

Vue portal scaffold directories prepared:
- frontend/web-portal/src/api
- frontend/web-portal/src/views
- frontend/web-portal/src/components
- frontend/web-portal/src/stores
- frontend/web-portal/src/router
- frontend/web-portal/src/utils
- frontend/web-portal/src/styles
- frontend/web-portal/public

## Engineering Constraints Added

Constraint and intent documents added:
- backend/README.md
- backend/java/README.md
- backend/python/README.md
- deploy/README.md
- sql/README.md
- scripts/README.md

## Next Step (Stage 2)

Start local infrastructure baseline using Docker Compose profiles:
- PostgreSQL
- Redis
- MinIO
- Kafka

Then add environment wiring templates under deploy/docker-compose/base and deploy/docker-compose/profiles.
