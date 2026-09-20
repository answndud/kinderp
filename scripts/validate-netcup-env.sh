#!/usr/bin/env sh
set -eu
: "$1"
env_file="$1"
[ -r "$env_file" ] || { echo "env file is not readable" >&2; exit 1; }

app_image_override="${APP_IMAGE-}"
app_version_override="${APP_VERSION-}"
set -a
. "$env_file"
set +a
if [ -n "$app_image_override" ]; then APP_IMAGE="$app_image_override"; fi
if [ -n "$app_version_override" ]; then APP_VERSION="$app_version_override"; fi

required="APP_DOMAIN APP_IMAGE APP_VERSION MYSQL_ROOT_PASSWORD MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD FLYWAY_DB_USERNAME FLYWAY_DB_PASSWORD REDIS_PASSWORD CORS_ALLOWED_ORIGINS JWT_SECRET NOTIFICATION_EMAIL_ENABLED NOTIFICATION_EMAIL_FROM SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD APP_PUBLIC_DEMO_ENABLED APP_SEED_ENABLED"
for name in $required; do
  value=$(printenv "$name" 2>/dev/null || true)
  [ -n "$value" ] || { echo "missing required variable: $name" >&2; exit 1; }
  case "$value" in
    *replace-with*|*placeholder*|*example.com*|*example.test*) echo "placeholder value is not allowed: $name" >&2; exit 1 ;;
  esac
done

case "$APP_DOMAIN" in *://*|*/*) echo "APP_DOMAIN must be a hostname" >&2; exit 1 ;; esac
case "$CORS_ALLOWED_ORIGINS" in https://*) ;; *) echo "CORS_ALLOWED_ORIGINS must use https" >&2; exit 1 ;; esac
[ "$MYSQL_USER" != "root" ] || { echo "application MySQL user must not be root" >&2; exit 1; }
[ "$(printf %s "$MYSQL_PASSWORD" | wc -c | tr -d ' ')" -ge 16 ] || { echo "MYSQL_PASSWORD must be at least 16 characters" >&2; exit 1; }
[ "$FLYWAY_DB_USERNAME" != "$MYSQL_USER" ] || { echo "Flyway user must differ from application user" >&2; exit 1; }
[ "$FLYWAY_DB_USERNAME" != "root" ] || { echo "Flyway user must not be root" >&2; exit 1; }
[ "$(printf %s "$FLYWAY_DB_PASSWORD" | wc -c | tr -d ' ')" -ge 16 ] || { echo "FLYWAY_DB_PASSWORD must be at least 16 characters" >&2; exit 1; }
[ "$(printf %s "$REDIS_PASSWORD" | wc -c | tr -d ' ')" -ge 16 ] || { echo "REDIS_PASSWORD must be at least 16 characters" >&2; exit 1; }
[ "$(printf %s "$JWT_SECRET" | wc -c | tr -d ' ')" -ge 32 ] || { echo "JWT_SECRET is too short" >&2; exit 1; }
[ "$NOTIFICATION_EMAIL_ENABLED" = "true" ] || { echo "NOTIFICATION_EMAIL_ENABLED must be true for the netcup production profile" >&2; exit 1; }
[ "$APP_PUBLIC_DEMO_ENABLED" = "true" ] || { echo "APP_PUBLIC_DEMO_ENABLED must be true for the public portfolio demo" >&2; exit 1; }
[ "$APP_SEED_ENABLED" = "true" ] || { echo "APP_SEED_ENABLED must be true for the public portfolio demo" >&2; exit 1; }
[ "$SPRING_MAIL_HOST" = "smtp.resend.com" ] || { echo "SPRING_MAIL_HOST must be smtp.resend.com for the Resend deployment" >&2; exit 1; }
[ "$(printf %s "$SPRING_MAIL_PASSWORD" | wc -c | tr -d ' ')" -ge 12 ] || { echo "SPRING_MAIL_PASSWORD must be at least 12 characters" >&2; exit 1; }
[ "${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE:-}" = "true" ] || { echo "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE must be true" >&2; exit 1; }

echo "ERP netcup env policy valid: $env_file"
