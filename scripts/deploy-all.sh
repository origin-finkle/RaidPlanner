#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/raid-planner-ui"

echo "==> Mise a jour du backend"
git -C "$ROOT_DIR" pull

echo "==> Mise a jour du frontend"
git -C "$FRONTEND_DIR" pull

echo "==> Rebuild et redeploiement du backend et du frontend"
docker compose \
  --env-file "$ROOT_DIR/.env.prod" \
  -f "$ROOT_DIR/docker-compose.prod.yml" \
  up -d --build backend frontend

echo "==> Backend et frontend redeployes"
