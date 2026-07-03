#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

backend_image="knowledge-rag-backend:prod"
frontend_image="knowledge-rag-frontend:prod"
backend_temp="knowledge-rag-backend-patch"
frontend_temp="knowledge-rag-frontend-patch"

cleanup() {
  docker rm -f "$backend_temp" "$frontend_temp" >/dev/null 2>&1 || true
}
trap cleanup EXIT

test -f deployment-patch/app.jar || test -f deployment-patch/frontend/index.html

cleanup

services=()

if test -f deployment-patch/app.jar; then
  docker image inspect "$backend_image" >/dev/null
  docker create --name "$backend_temp" "$backend_image" >/dev/null
  docker cp deployment-patch/app.jar "$backend_temp":/app/app.jar
  docker commit "$backend_temp" "$backend_image" >/dev/null
  services+=(backend)
fi

if test -f deployment-patch/frontend/index.html; then
  docker image inspect "$frontend_image" >/dev/null
  docker create --name "$frontend_temp" "$frontend_image" >/dev/null
  docker cp deployment-patch/frontend/. "$frontend_temp":/usr/share/nginx/html/
  docker commit "$frontend_temp" "$frontend_image" >/dev/null
  services+=(frontend)
fi

docker compose --env-file .env -f docker-compose.prod.yml \
  up -d --no-build --no-deps --force-recreate "${services[@]}"

docker compose --env-file .env -f docker-compose.prod.yml ps
