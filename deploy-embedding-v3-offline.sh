#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

test -f .env
test -f deployment-patch/app.jar

set_env() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" .env; then
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    printf '\n%s=%s\n' "$key" "$value" >> .env
  fi
}

cp .env ".env.before-embedding-v3-$(date +%Y%m%d-%H%M%S)"
set_env EMBEDDING_MODEL_NAME text-embedding-v3
set_env EMBEDDING_MODEL_DIMENSIONS 1024
set_env PGVECTOR_DIMENSIONS 1024

bash deploy-member-fix-offline.sh

echo "Embedding model switched to text-embedding-v3 (1024 dimensions)."
echo "Open each knowledge base and run vector synchronization once to rebuild old vectors."
