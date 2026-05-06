#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

git pull

if [ -d raid-planner-ui/.git ]; then
  git -C raid-planner-ui pull
fi

docker compose --env-file .env.prod -f docker-compose.prod.yml up -d monitoring
