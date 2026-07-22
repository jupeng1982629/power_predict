# Local Infra Access Guide

## 1. Components With Web Console

| Component | URL | Account |
|---|---|---|
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| MLflow UI | http://localhost:15000 | No login by default |
| Keycloak Admin | http://localhost:18081/admin/ | admin / admin123! |
| Keycloak Realm User | http://localhost:18081/realms/power-predict/account/ | demo.admin / Demo@123456 |

## 2. Components Without Built-in Web Console

| Component | Connection |
|---|---|
| PostgreSQL | host=localhost port=5432 db=power_predict user=power_predict password=power_predict |
| Redis | localhost:6379 |
| Kafka | localhost:9092 |

## 3. Portal Entry

After starting frontend dev server, open:

http://localhost:5260/infra-portal.html

This page is located at:

frontend/web-portal/public/infra-portal.html

## 4. Notes

- Port values above match current local setup.
- If you change ports in deploy/docker-compose/.env.local, update the portal page and this guide accordingly.
