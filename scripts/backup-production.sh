#!/usr/bin/env bash

set -Eeuo pipefail

if [[ "${1:-}" == "--dry-run" ]]; then
    cat <<'EOF'
Required runtime inputs:
  BACKUP_DIR, MYSQL_HOST, MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD, REDIS_PASSWORD
  MYSQLDUMP_BIN, COMPOSE_ENV_FILE, COMPOSE_FILE
The real run creates a MySQL logical dump, a Redis RDB snapshot, and SHA-256 checksums.
EOF
    exit 0
fi

: "${BACKUP_DIR:?BACKUP_DIR is required}"
: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"

case "$BACKUP_DIR" in
    /*) ;;
    *)
        echo "BACKUP_DIR must be an absolute path" >&2
        exit 1
        ;;
esac

normalized_backup_dir="${BACKUP_DIR%/}"
if [[ -z "$normalized_backup_dir" || "$normalized_backup_dir" == "/" ]]; then
    echo "BACKUP_DIR must not be the filesystem root" >&2
    exit 1
fi
BACKUP_DIR="$normalized_backup_dir"

MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-.env.prod}"
REDIS_SERVICE="${REDIS_SERVICE:-redis}"

command -v "$MYSQLDUMP_BIN" >/dev/null || { echo "mysqldump is required" >&2; exit 1; }
command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum is required" >&2; exit 1; }

umask 077
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$BACKUP_DIR/$stamp"
staging_dir="$BACKUP_DIR/.${stamp}.$$.incomplete"
mkdir -p "$BACKUP_DIR"

if [[ -e "$backup_dir" ]]; then
    echo "Backup destination already exists: $backup_dir" >&2
    exit 1
fi
mkdir "$staging_dir"

completed=0
cleanup() {
    if [[ "$completed" -ne 1 && -d "$staging_dir" ]]; then
        rm -rf -- "$staging_dir"
    fi
}
trap cleanup EXIT

MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQLDUMP_BIN" \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --single-transaction \
    --quick \
    --routines \
    --events \
    --no-tablespaces \
    --set-gtid-purged=OFF \
    "$MYSQL_DATABASE" > "$staging_dir/mysql.sql"

compose() {
    docker compose --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

redis_container="$(compose ps -q "$REDIS_SERVICE")"
[[ -n "$redis_container" ]] || { echo "Redis container is not running" >&2; exit 1; }
redis_path="/tmp/erp-backup-$stamp.rdb"
compose exec -T \
    -e REDISCLI_AUTH="$REDIS_PASSWORD" \
    "$REDIS_SERVICE" redis-cli \
    --no-auth-warning \
    --rdb "$redis_path" >/dev/null
docker cp "$redis_container:$redis_path" "$staging_dir/redis.rdb"
compose exec -T "$REDIS_SERVICE" rm -f "$redis_path" >/dev/null

(cd "$staging_dir" && sha256sum mysql.sql redis.rdb > SHA256SUMS && sha256sum --check SHA256SUMS)
mv -- "$staging_dir" "$backup_dir"
completed=1
printf 'Backup created: %s\n' "$backup_dir"
