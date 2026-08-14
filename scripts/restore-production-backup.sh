#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
    cat >&2 <<'EOF'
Usage:
  restore-production-backup.sh BACKUP_DIR \
    --mysql-container NAME --redis-container NAME \
    --mysql-database NAME --mysql-user NAME \
    [--mysql-assert-query SQL --mysql-assert-expected VALUE] \
    [--redis-assert-key KEY --redis-assert-expected VALUE] \
    [--redis-rdb-path PATH] --confirm-disposable

Required environment variables:
  MYSQL_PASSWORD, REDIS_PASSWORD

Both target containers must have the label:
  kindergarten.erp.restore-target=disposable
EOF
    exit 2
}

backup_dir="${1:-}"
[[ -n "$backup_dir" ]] || usage
shift

mysql_container=""
redis_container=""
mysql_database=""
mysql_user=""
redis_rdb_path="/data/dump.rdb"
mysql_assert_query=""
mysql_assert_expected=""
redis_assert_key=""
redis_assert_expected=""
confirmed=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --mysql-container)
            mysql_container="${2:-}"
            shift 2
            ;;
        --redis-container)
            redis_container="${2:-}"
            shift 2
            ;;
        --mysql-database)
            mysql_database="${2:-}"
            shift 2
            ;;
        --mysql-user)
            mysql_user="${2:-}"
            shift 2
            ;;
        --mysql-assert-query)
            mysql_assert_query="${2:-}"
            shift 2
            ;;
        --mysql-assert-expected)
            mysql_assert_expected="${2:-}"
            shift 2
            ;;
        --redis-assert-key)
            redis_assert_key="${2:-}"
            shift 2
            ;;
        --redis-assert-expected)
            redis_assert_expected="${2:-}"
            shift 2
            ;;
        --redis-rdb-path)
            redis_rdb_path="${2:-}"
            shift 2
            ;;
        --confirm-disposable)
            confirmed=1
            shift
            ;;
        *)
            usage
            ;;
    esac
done

: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"

[[ "$confirmed" -eq 1 ]] || {
    echo "Refusing restore without --confirm-disposable" >&2
    exit 1
}
[[ -n "$mysql_container" && -n "$redis_container" && -n "$mysql_database" && -n "$mysql_user" ]] || usage
[[ -z "$mysql_assert_query" || -n "$mysql_assert_expected" ]] || {
    echo "--mysql-assert-expected is required with --mysql-assert-query" >&2
    exit 1
}
[[ -z "$mysql_assert_expected" || -n "$mysql_assert_query" ]] || {
    echo "--mysql-assert-query is required with --mysql-assert-expected" >&2
    exit 1
}
[[ -z "$redis_assert_key" || -n "$redis_assert_expected" ]] || {
    echo "--redis-assert-expected is required with --redis-assert-key" >&2
    exit 1
}
[[ -z "$redis_assert_expected" || -n "$redis_assert_key" ]] || {
    echo "--redis-assert-key is required with --redis-assert-expected" >&2
    exit 1
}
[[ "$backup_dir" = /* && "$backup_dir" != "/" ]] || {
    echo "BACKUP_DIR must be an absolute path other than /" >&2
    exit 1
}
[[ -f "$backup_dir/mysql.sql" && -s "$backup_dir/mysql.sql" ]] || {
    echo "mysql.sql is missing or empty: $backup_dir" >&2
    exit 1
}
[[ -f "$backup_dir/redis.rdb" && -s "$backup_dir/redis.rdb" ]] || {
    echo "redis.rdb is missing or empty: $backup_dir" >&2
    exit 1
}

command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }

container_label() {
    docker inspect --format '{{index .Config.Labels "kindergarten.erp.restore-target"}}' "$1" 2>/dev/null || true
}

for container in "$mysql_container" "$redis_container"; do
    [[ "$(container_label "$container")" == "disposable" ]] || {
        echo "Refusing container without kindergarten.erp.restore-target=disposable: $container" >&2
        exit 1
    }
    [[ "$(docker inspect --format '{{.State.Status}}' "$container")" == "running" ]] || {
        echo "Container must be running before restore: $container" >&2
        exit 1
    }
done

echo "Verifying backup checksums..."
"$(dirname "$0")/verify-production-backup.sh" "$backup_dir"

echo "Waiting for disposable MySQL..."
MYSQL_PWD="$MYSQL_PASSWORD" docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$mysql_container" \
    mysqladmin ping --silent --user="$mysql_user" --host=127.0.0.1 >/dev/null

echo "Restoring MySQL logical dump into $mysql_container..."
docker exec -i -e MYSQL_PWD="$MYSQL_PASSWORD" "$mysql_container" \
    mysql --user="$mysql_user" --host=127.0.0.1 "$mysql_database" < "$backup_dir/mysql.sql"

if [[ -n "$mysql_assert_query" ]]; then
    mysql_assert_actual="$(docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$mysql_container" \
        mysql --batch --skip-column-names --user="$mysql_user" --host=127.0.0.1 \
        "$mysql_database" --execute="$mysql_assert_query")"
    [[ "$mysql_assert_actual" == "$mysql_assert_expected" ]] || {
        echo "MySQL restore assertion failed: expected '$mysql_assert_expected', got '$mysql_assert_actual'" >&2
        exit 1
    }
    echo "MySQL restore assertion passed."
fi

echo "Restoring Redis RDB into $redis_container..."
docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$redis_container" \
    redis-cli --no-auth-warning ping >/dev/null
docker stop "$redis_container" >/dev/null
docker cp "$backup_dir/redis.rdb" "$redis_container:$redis_rdb_path"
docker start "$redis_container" >/dev/null
docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$redis_container" \
    redis-cli --no-auth-warning ping >/dev/null

if [[ -n "$redis_assert_key" ]]; then
    redis_assert_actual="$(docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$redis_container" \
        redis-cli --no-auth-warning --raw get "$redis_assert_key")"
    [[ "$redis_assert_actual" == "$redis_assert_expected" ]] || {
        echo "Redis restore assertion failed for key '$redis_assert_key': expected '$redis_assert_expected', got '$redis_assert_actual'" >&2
        exit 1
    }
    echo "Redis restore assertion passed."
fi

echo "Disposable restore completed: MySQL and Redis are healthy."
