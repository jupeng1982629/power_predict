# gateway-service

Minimal gateway implementation for local demo flow.

Current scope:

- expose `/api/demo/summary`
- call system-service over HTTP
- provide a stable entry for frontend integration

Run locally from `backend/java`:

1. set `JAVA_HOME` to a JDK 17 installation
2. run `mvn -pl gateway-service -am spring-boot:run`
