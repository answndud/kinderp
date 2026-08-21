#!/usr/bin/env bash

set -Eeuo pipefail

: "${REDIS_CONTAINER:?REDIS_CONTAINER is required}"
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"
[[ "${1:-}" == "--confirm-all-sessions" ]] || {
    echo "Refusing to revoke sessions without --confirm-all-sessions" >&2
    exit 2
}
command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }

count=0
while IFS= read -r key; do
    [[ -n "$key" ]] || continue
    docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$REDIS_CONTAINER" \
        redis-cli --no-auth-warning del "$key" >/dev/null
    count=$((count + 1))
done < <(
    docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$REDIS_CONTAINER" \
        redis-cli --no-auth-warning --scan --pattern 'refresh:*'
)

printf 'Revoked %s refresh/session keys.\n' "$count"
