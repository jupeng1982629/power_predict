# Java Services

Service list:
- gateway-service
- plant-service
- monitor-service
- forecast-service
- system-service
- common-libs

Package layering constraints per service:
- api
- application
- domain
- infrastructure
- security

Notes:
- Security concerns stay in the security layer.
- Outbound integrations stay in infrastructure clients.
- Keep service ownership aligned with docs/01-system-modules-and-service-boundaries.md.
