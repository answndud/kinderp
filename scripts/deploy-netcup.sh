#!/usr/bin/env bash
set -Eeuo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.netcup.yml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-deploy/.env.netcup.example}"
ENV_VALIDATOR="${ENV_VALIDATOR:-scripts/validate-netcup-env.sh}"
DEPLOY_LOCK_FILE="${DEPLOY_LOCK_FILE:-/tmp/portfolio-deploy.lock}"
SMOKE_URL="${SMOKE_URL:-}"
PREFLIGHT_ONLY="${PREFLIGHT_ONLY:-0}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-30}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"

compose() {
  docker compose --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

[[ -f "$COMPOSE_FILE" && -f "$COMPOSE_ENV_FILE" && -x "$ENV_VALIDATOR" ]] || {
  echo "Compose file or env file is missing" >&2
  exit 1
}
"$ENV_VALIDATOR" "$COMPOSE_ENV_FILE"
if command -v flock >/dev/null; then
  exec 9>"$DEPLOY_LOCK_FILE"
  flock -n 9 || { echo "another portfolio deployment is already running" >&2; exit 1; }
elif [[ "${ALLOW_UNSERIALIZED_DEPLOY:-0}" == "1" ]]; then
  echo "warning: flock is unavailable; deployment is explicitly running without a lock" >&2
else
  echo "flock is required for deployment serialization (or set ALLOW_UNSERIALIZED_DEPLOY=1 for local rehearsal)" >&2
  exit 1
fi
docker network inspect edge >/dev/null 2>&1 || docker network create edge >/dev/null
compose config >/dev/null

if [[ "$PREFLIGHT_ONLY" == "1" ]]; then
  echo "netcup deployment preflight passed"
  exit 0
fi

previous_image="$(docker inspect --format '{{.Config.Image}}' kindergarten-erp-app 2>/dev/null || true)"
compose pull
compose up -d mysql redis app caddy

ready=1
for _ in $(seq 1 "$MAX_ATTEMPTS"); do
  health="$(docker inspect --format '{{.State.Health.Status}}' kindergarten-erp-app 2>/dev/null || true)"
  if [[ "$health" == "healthy" ]]; then
    ready=0
    break
  fi
  sleep "$SLEEP_SECONDS"
done

if [[ "$ready" -eq 0 && -n "$SMOKE_URL" ]]; then
  curl --fail --silent --show-error --location --max-time 10 "$SMOKE_URL" >/dev/null || ready=1
fi

if [[ "$ready" -eq 0 ]]; then
  echo "netcup ERP deployment ready"
  exit 0
fi

echo "deployment failed; attempting application image rollback" >&2
if [[ -z "$previous_image" ]]; then
  compose ps >&2 || true
  compose logs --tail=200 app >&2 || true
  exit 1
fi
APP_IMAGE="$previous_image" compose up -d app caddy
for _ in $(seq 1 "$MAX_ATTEMPTS"); do
  health="$(docker inspect --format '{{.State.Health.Status}}' kindergarten-erp-app 2>/dev/null || true)"
  [[ "$health" == "healthy" ]] && {
    echo "rollback restored $previous_image" >&2
    exit 1
  }
  sleep "$SLEEP_SECONDS"
done
compose ps >&2 || true
compose logs --tail=200 app >&2 || true
echo "rollback failed" >&2
exit 1
