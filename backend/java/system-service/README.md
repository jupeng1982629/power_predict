# system-service

Minimal backend implementation for local demo flow.

Current scope:

- expose `/api/demo/summary`
- return basic platform summary data

Run locally from `backend/java`:

1. set `JAVA_HOME` to a JDK 17 installation
2. run `mvn -pl system-service -am spring-boot:run`
