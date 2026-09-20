#!/usr/bin/env bash
set -Eeuo pipefail

# Creates separate application and Flyway accounts and applies least-privilege grants.
# Set MYSQL_CONTAINER to execute through the MySQL container's local socket, or leave it
# empty and provide MYSQL_HOST/MYSQL_PORT for a directly reachable MySQL server.

: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${FLYWAY_DB_USERNAME:?FLYWAY_DB_USERNAME is required}"
: "${FLYWAY_DB_PASSWORD:?FLYWAY_DB_PASSWORD is required}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_ROOT_USERNAME="${MYSQL_ROOT_USERNAME:-root}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-}"

validate_identifier() {
    [[ "$1" =~ ^[A-Za-z0-9_]+$ ]] || {
        echo "MySQL identifier contains unsupported characters: $1" >&2
        exit 1
    }
}

sql_escape() {
    local value="$1"
    value="${value//\'/\'\'}"
    printf '%s' "$value"
}

validate_identifier "$MYSQL_DATABASE"
validate_identifier "$MYSQL_USER"
validate_identifier "$FLYWAY_DB_USERNAME"
[[ "$MYSQL_USER" != "$FLYWAY_DB_USERNAME" ]] || {
    echo "application and Flyway users must be different" >&2
    exit 1
}
[[ "$MYSQL_USER" != "$MYSQL_ROOT_USERNAME" && "$FLYWAY_DB_USERNAME" != "$MYSQL_ROOT_USERNAME" ]] || {
    echo "application and Flyway users must not be the root user" >&2
    exit 1
}

database="$(sql_escape "$MYSQL_DATABASE")"
app_user="$(sql_escape "$MYSQL_USER")"
app_password="$(sql_escape "$MYSQL_PASSWORD")"
flyway_user="$(sql_escape "$FLYWAY_DB_USERNAME")"
flyway_password="$(sql_escape "$FLYWAY_DB_PASSWORD")"

read -r -d '' grant_sql <<SQL || true
CREATE USER IF NOT EXISTS '$app_user'@'%' IDENTIFIED BY '$app_password';
ALTER USER '$app_user'@'%' IDENTIFIED BY '$app_password';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$app_user'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION ON \`$database\`.* FROM '$app_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$database\`.* TO '$app_user'@'%';

CREATE USER IF NOT EXISTS '$flyway_user'@'%' IDENTIFIED BY '$flyway_password';
ALTER USER '$flyway_user'@'%' IDENTIFIED BY '$flyway_password';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$flyway_user'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION ON \`$database\`.* FROM '$flyway_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON \`$database\`.* TO '$flyway_user'@'%';

FLUSH PRIVILEGES;
SQL

if [[ -n "$MYSQL_CONTAINER" ]]; then
    docker exec -i \
        -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
        "$MYSQL_CONTAINER" \
        mysql --protocol=socket --user="$MYSQL_ROOT_USERNAME" --database="$MYSQL_DATABASE" <<<"$grant_sql"
else
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
        --host="$MYSQL_HOST" \
        --port="$MYSQL_PORT" \
        --user="$MYSQL_ROOT_USERNAME" \
        --database="$MYSQL_DATABASE" <<<"$grant_sql"
fi

echo "MySQL least-privilege grants applied: app=$MYSQL_USER flyway=$FLYWAY_DB_USERNAME database=$MYSQL_DATABASE"
