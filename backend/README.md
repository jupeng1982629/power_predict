# Backend Workspace

This folder contains all backend services.

- java/: platform domain services (gateway, plant, monitor, forecast, system)
- python/: algorithm domain services (feature, model, inference)

Engineering constraints:
- Follow service boundaries in docs/01-system-modules-and-service-boundaries.md.
- Keep DTO and domain objects separated.
- Shared utilities must go under language-specific shared modules.
