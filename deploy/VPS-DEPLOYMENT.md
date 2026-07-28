# VPS deployment (1 CPU / 2 GB RAM)

1. Clone the project to `/opt/shlyapoff` and copy `.env.example` to `.env` with real secrets and the HTTPS `APP_BASE_URL`.
2. Install host Nginx, Certbot and ImageMagick. Copy `deploy/nginx/shlyapoff.conf.example` to Nginx, replace placeholders, validate with `nginx -t`, then reload Nginx.
3. Build and start the application with `docker compose up -d --build`. The application remains bound to `127.0.0.1:8080`.
4. Run `sh scripts/backfill-thumbnails.sh` once to generate miniatures for existing images and fill `image_thumbnail_url`.
5. Verify `curl -fsS http://127.0.0.1:8080/actuator/health`, `docker compose ps`, and Nginx access logs. Do not expose `/actuator` publicly.

Performance acceptance checks (run only after the catalog contains representative data):

```sh
docker compose exec -T db psql -U shlyapoff_user -d shlyapoff_db < scripts/verify-performance.sql
BASE_URL=http://127.0.0.1:8080 k6 run scripts/load-test.js
docker stats --no-stream shlyapoff_app shlyapoff_db
curl -I https://example.com/images/thumbs/example.jpg
```

The plans should show the partial catalog indexes (and `idx_products_active_name_trgm` for substring search where it is selective). The k6 script fails if either home or catalog P95 exceeds 300 ms or a response is not successful.

The files under `uploads/originals-*` are backups. Move them outside `/opt/shlyapoff/uploads` before enabling Nginx aliasing, otherwise they are addressable as static files.
