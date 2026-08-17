#!/usr/bin/env bash

set -Eeuo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-.env.prod}"
READINESS_URL="${READINESS_URL:-http://127.0.0.1:9091/actuator/health/readiness}"
SMOKE_URL="${SMOKE_URL:-}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-30}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"
PREFLIGHT_ONLY="${PREFLIGHT_ONLY:-0}"

[[ "$MAX_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || {
    echo "MAX_ATTEMPTS must be a positive integer" >&2
    exit 1
}
[[ "$SLEEP_SECONDS" =~ ^[0-9]+$ ]] || {
    echo "SLEEP_SECONDS must be a non-negative integer" >&2
    exit 1
}
[[ "$PREFLIGHT_ONLY" =~ ^[01]$ ]] || {
    echo "PREFLIGHT_ONLY must be 0 or 1" >&2
    exit 1
}

compose() {
    docker compose --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

validate_preflight() {
    command -v docker >/dev/null || {
        echo "docker is required" >&2
        return 1
    }
    command -v curl >/dev/null || {
        echo "curl is required" >&2
        return 1
    }
    [[ -f "$COMPOSE_FILE" ]] || {
        echo "Compose file does not exist: $COMPOSE_FILE" >&2
        return 1
    }
    [[ -f "$COMPOSE_ENV_FILE" ]] || {
        echo "Compose env file does not exist: $COMPOSE_ENV_FILE" >&2
        return 1
    }
    compose config >/dev/null || {
        echo "Compose configuration is invalid: $COMPOSE_FILE" >&2
        return 1
    }
}

wait_for_readiness() {
    local attempt
    for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
        if curl --fail --silent --show-error --max-time 5 "$READINESS_URL" >/dev/null; then
            return 0
        fi
        sleep "$SLEEP_SECONDS"
    done
    return 1
}

run_smoke() {
    if [[ -z "$SMOKE_URL" ]]; then
        echo "Deployment smoke URL is not configured; readiness check is the release gate."
        return 0
    fi

    curl --fail --silent --show-error --location --max-redirs 3 --max-time 10 "$SMOKE_URL" >/dev/null
}

validate_smoke_url() {
    [[ -z "$SMOKE_URL" ]] && return 0
    case "$SMOKE_URL" in
        http://*|https://*) ;;
        *)
            echo "SMOKE_URL must use http:// or https://" >&2
            return 1
            ;;
    esac
}

wait_for_deployment() {
    wait_for_readiness && run_smoke
}

validate_smoke_url
validate_preflight

if [[ "$PREFLIGHT_ONLY" == "1" ]]; then
    echo "Deployment preflight passed for ${COMPOSE_FILE}."
    exit 0
fi

previous_image="$(docker inspect --format '{{.Config.Image}}' kinderp-app 2>/dev/null || true)"
previous_version="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' kinderp-app 2>/dev/null \
    | sed -n 's/^APP_VERSION=//p' | head -n 1 || true)"

compose pull
compose up -d

if wait_for_deployment; then
    echo "Deployment became ready with the image configured by ${COMPOSE_ENV_FILE}."
    exit 0
fi

echo "Deployment failed readiness; starting rollback." >&2
if [[ -z "$previous_image" ]]; then
    echo "No previous app image was found; manual recovery is required." >&2
    compose ps >&2 || true
    compose logs --tail=200 app >&2 || true
    exit 1
fi

APP_IMAGE="$previous_image" APP_VERSION="${previous_version:-unknown}" compose up -d
if ! wait_for_deployment; then
    echo "Rollback also failed readiness; manual recovery is required." >&2
    compose ps >&2 || true
    compose logs --tail=200 app >&2 || true
    exit 1
fi

echo "Rollback restored ${previous_image} with APP_VERSION=${previous_version:-unknown}; the new release was not promoted." >&2
exit 1
