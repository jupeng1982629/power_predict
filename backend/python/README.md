# Python Services

Service list:
- feature-service
- model-service
- inference-service
- shared

Layering constraints per service:
- app/api: request validation and response mapping only
- app/schemas: pydantic schemas
- app/services: business and algorithm orchestration
- app/repositories: persistence and external data access
- app/core: config, logging, dependency wiring

Inference service should stay stateless to support horizontal scaling.
