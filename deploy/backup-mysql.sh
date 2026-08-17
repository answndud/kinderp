#!/usr/bin/env sh
set -eu
umask 077

: "${MYSQL_CONTAINER:?set MYSQL_CONTAINER}"
: "${MYSQL_DATABASE:?set MYSQL_DATABASE}"
: "${MYSQL_USER:?set MYSQL_USER}"
: "${MYSQL_PASSWORD:?set MYSQL_PASSWORD}"
: "${BACKUP_DIR:=./backups}"

command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }
mkdir -p "$BACKUP_DIR"
backup_id="$(date -u +%Y%m%dT%H%M%SZ)"
backup_root="$BACKUP_DIR/erp-$backup_id"
mkdir -p "$backup_root"
backup_complete=0
cleanup_failed_backup() {
  if [ "$backup_complete" -ne 1 ] && [ -n "$backup_root" ] && [ -d "$backup_root" ]; then
    rm -rf -- "$backup_root"
  fi
}
trap cleanup_failed_backup EXIT HUP INT TERM

docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER" \
  mysqldump --single-transaction --no-tablespaces --routines --events --triggers \
  -u "$MYSQL_USER" --databases "$MYSQL_DATABASE" > "$backup_root/mysql.sql"
gzip -c "$backup_root/mysql.sql" > "$backup_root/mysql.sql.gz"
rm -f "$backup_root/mysql.sql"
[ -s "$backup_root/mysql.sql.gz" ] || { echo "mysql dump is empty" >&2; exit 1; }

{
  echo "backup_id=$backup_id"
  echo "created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "mysql_database=$MYSQL_DATABASE"
  echo "mysql_bytes=$(wc -c < "$backup_root/mysql.sql.gz" | tr -d ' ')"
} > "$backup_root/manifest.txt"
(cd "$backup_root" && find . -type f ! -name manifest.sha256 -print0 | sort -z | xargs -0 sha256sum > manifest.sha256)
backup_complete=1
echo "created ERP MySQL backup: $backup_root"
