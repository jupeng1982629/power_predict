# power_predict

光伏电站功率 AI 预测系统。

## Repository Layout

```text
power_predict/
  docs/
  backend/
    java/
    python/
  frontend/
    web-portal/
  data/
  sql/
  deploy/
  scripts/
  .github/workflows/
```

## Current Progress

- Stage 1 (repository scaffold and engineering constraints): completed baseline.
- See docs/06-stage1-scaffold-baseline.md for details.
- Stage 2 (local infrastructure baseline): compose files and local scripts completed.
- See docs/07-stage2-local-infra-baseline.md for details.
- Stage 3 (PostgreSQL schema and Hudi tables): baseline scripts created.
- See docs/08-stage3-database-and-hudi-baseline.md for details.
- Stage 4 (authentication base and seed data): local Keycloak + JWT resource server + real web login flow are available.

## Next

- Stage 5: API contract hardening, OpenAPI outputs, and frontend SDK/alignment.
