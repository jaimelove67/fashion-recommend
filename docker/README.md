# Local Docker Environment

This stack provides the local PostgreSQL and MinIO infrastructure, plus optional Spring Boot and Vue application containers.

| Service | Host address | Purpose |
| --- | --- | --- |
| PostgreSQL 16 | `localhost:5433` | Application data |
| MinIO API | `http://localhost:9000` | Private garment object storage |
| MinIO Console | `http://localhost:9001` | Local object-storage administration |
| Backend (`app` profile) | `http://localhost:8088` | Spring Boot API and health check |
| Frontend (`app` profile) | `http://localhost:8090` | Vue application with same-origin API proxy |

All published ports bind to `127.0.0.1`. PostgreSQL, MinIO, and the MinIO client are pinned by image digest; update the digests deliberately when applying upstream fixes. `minio-init` creates the private bucket once and exits with code 0 on success.

Use `docker compose up -d` to start the stack and `docker compose down` to stop it. `docker compose down -v` also deletes all local development data.

Copy `.env.example` to `.env` only when replacing the development defaults. Do not use the example passwords on a cloud server.

Run `docker compose --profile app up --build -d` to additionally build the Spring Boot API and Vue application. The containerized application is then available at `http://localhost:8090`; it proxies `/api` to the API container. Set `DASHSCOPE_API_KEY` only in a local `.env` or the cloud deployment secret store. Model calls remain disabled unless their corresponding `BAILIAN_ENABLED` or `BAILIAN_VISION_ENABLED` switch is enabled; vision also requires consent for each upload.

This loopback HTTP configuration uses `SESSION_COOKIE_SECURE=false`. For HTTPS deployment, set it to `true` and replace the development passwords. The backend source configuration uses the same local-HTTP-safe default, so refreshing a local page keeps the session without an extra environment variable.

Weather requests use `wttr.in` first and fall back to Open-Meteo. Successful snapshots are cached in-process for 15 minutes by default; provider URLs, timeouts, TTL, and maximum cache size can be overridden with the `WEATHER_*` variables documented in `.env.example`.

The backend exposes only `health` and `info` through Actuator. Check health at `http://localhost:8088/actuator/health`. Prometheus and metrics endpoints are not enabled in this local stack.
