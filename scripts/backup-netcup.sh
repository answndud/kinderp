#!/usr/bin/env bash

set -Eeuo pipefail

: "${BACKUP_DIR:?BACKUP_DIR is required}"
: "${MYSQL_CONTAINER:?MYSQL_CONTAINER is required}"
: "${REDIS_CONTAINER:?REDIS_CONTAINER is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"

BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
[[ "$BACKUP_DIR" = /* && "$BACKUP_DIR" != "/" ]] || {
    echo "BACKUP_DIR must be an absolute path other than /" >&2
    exit 1
}
[[ "$BACKUP_RETENTION_DAYS" =~ ^[1-9][0-9]*$ ]] || {
    echo "BACKUP_RETENTION_DAYS must be a positive integer" >&2
    exit 1
}

command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum is required" >&2; exit 1; }

umask 077
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$BACKUP_DIR/$stamp"
staging_dir="$BACKUP_DIR/.${stamp}.$$.incomplete"
redis_path="/tmp/kinderp-backup-$stamp.rdb"
mkdir -p "$BACKUP_DIR"
[[ ! -e "$backup_dir" ]] || { echo "Backup destination already exists: $backup_dir" >&2; exit 1; }
mkdir "$staging_dir"

completed=0
cleanup() {
    if [[ "$completed" -ne 1 && -d "$staging_dir" ]]; then
        rm -rf -- "$staging_dir"
    fi
}
trap cleanup EXIT

docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER" \
    mysqldump --single-transaction --quick --routines --events --no-tablespaces \
    --set-gtid-purged=OFF --user="$MYSQL_USER" --databases "$MYSQL_DATABASE" \
    > "$staging_dir/mysql.sql"

docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$REDIS_CONTAINER" \
    redis-cli --no-auth-warning --rdb "$redis_path" >/dev/null
docker cp "$REDIS_CONTAINER:$redis_path" "$staging_dir/redis.rdb"
docker exec "$REDIS_CONTAINER" rm -f "$redis_path" >/dev/null

[[ -s "$staging_dir/mysql.sql" && -s "$staging_dir/redis.rdb" ]] || {
    echo "backup artifact is missing or empty" >&2
    exit 1
}
(cd "$staging_dir" && sha256sum mysql.sql redis.rdb > SHA256SUMS && sha256sum --check SHA256SUMS)
mv -- "$staging_dir" "$backup_dir"
completed=1

find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d \
    -name '20????????T??????Z' -mtime "+$BACKUP_RETENTION_DAYS" \
    -exec rm -rf -- {} +

printf 'Netcup backup created: %s (retention=%s days)\n' "$backup_dir" "$BACKUP_RETENTION_DAYS"
