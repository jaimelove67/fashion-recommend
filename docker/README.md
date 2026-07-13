# Local Docker Environment

This stack provides the local infrastructure required before the Spring Boot and Vue applications exist.

| Service | Host address | Purpose |
| --- | --- | --- |
| PostgreSQL 16 + pgvector | `localhost:5433` | Application data and vector search |
| Redis 7.4 | `localhost:6380` | Cache, rate limits, and trend snapshots |
| MinIO API | `http://localhost:9000` | Private garment object storage |
| MinIO Console | `http://localhost:9001` | Local object-storage administration |

The host ports are intentionally different from common defaults: the existing Windows Redis 3.2 service continues to use `6379` during migration.

Use `docker compose up -d` to start the stack and `docker compose down` to stop it. `docker compose down -v` also deletes all local development data.

Copy `.env.example` to `.env` only when replacing the development defaults. Do not use the example passwords on a cloud server.

Run `docker compose --profile app up --build -d` to additionally build the Spring Boot API and Vue application. The containerized application is then available at `http://localhost:8090`; it proxies `/api` to the API container. Set `DASHSCOPE_API_KEY` only in a local `.env` or the cloud deployment secret store.

Weather requests use `wttr.in` first and fall back to Open-Meteo. Successful snapshots are cached in-process for 15 minutes by default; provider URLs, timeouts, TTL, and maximum cache size can be overridden with the `WEATHER_*` variables documented in `.env.example`.

Operational metrics are available on the loopback-bound backend at `http://localhost:8088/actuator/metrics` and `http://localhost:8088/actuator/prometheus`. Provider metrics use the `fashion.weather.*` prefix, and cache metrics use the `weather-current` cache tag.
