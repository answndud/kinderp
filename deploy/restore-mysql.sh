#!/usr/bin/env sh
set -eu

: "${BACKUP_ROOT:?set BACKUP_ROOT to a backup directory}"
: "${MYSQL_CONTAINER:?set MYSQL_CONTAINER}"
: "${MYSQL_DATABASE:?set MYSQL_DATABASE}"
: "${MYSQL_USER:?set MYSQL_USER}"
: "${MYSQL_PASSWORD:?set MYSQL_PASSWORD}"
: "${ALLOW_DESTRUCTIVE_RESTORE:?set ALLOW_DESTRUCTIVE_RESTORE=YES}"

[ "$ALLOW_DESTRUCTIVE_RESTORE" = "YES" ] || {
  echo "refusing MySQL restore: set ALLOW_DESTRUCTIVE_RESTORE=YES" >&2
  exit 1
}
[ -s "$BACKUP_ROOT/mysql.sql.gz" ] || { echo "missing mysql.sql.gz" >&2; exit 1; }
[ -f "$BACKUP_ROOT/manifest.sha256" ] || { echo "missing manifest.sha256" >&2; exit 1; }
(cd "$BACKUP_ROOT" && sha256sum -c manifest.sha256)

gzip -dc "$BACKUP_ROOT/mysql.sql.gz" |   docker exec -i -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER"   mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"
echo "restored ERP MySQL backup: $BACKUP_ROOT"

