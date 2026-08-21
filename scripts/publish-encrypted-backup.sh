#!/usr/bin/env bash

set -Eeuo pipefail

backup_dir="${1:-}"
: "${backup_dir:?usage: publish-encrypted-backup.sh BACKUP_DIRECTORY}"
: "${AGE_RECIPIENT:?AGE_RECIPIENT is required}"
: "${BACKUP_REMOTE_HOST:?BACKUP_REMOTE_HOST is required}"
: "${BACKUP_REMOTE_USER:?BACKUP_REMOTE_USER is required}"
: "${BACKUP_REMOTE_DIR:?BACKUP_REMOTE_DIR is required}"
: "${BACKUP_SSH_KEY:?BACKUP_SSH_KEY is required}"
: "${BACKUP_KNOWN_HOSTS_FILE:?BACKUP_KNOWN_HOSTS_FILE is required}"

[[ -d "$backup_dir" && "$backup_dir" = /* ]] || { echo "backup directory must be an absolute directory" >&2; exit 1; }
[[ -f "$backup_dir/SHA256SUMS" ]] || { echo "backup checksum file is missing" >&2; exit 1; }
[[ "$BACKUP_REMOTE_DIR" =~ ^/[A-Za-z0-9._/-]+$ ]] || { echo "BACKUP_REMOTE_DIR must be a safe absolute path" >&2; exit 1; }
[[ -r "$BACKUP_SSH_KEY" && -r "$BACKUP_KNOWN_HOSTS_FILE" ]] || { echo "SSH key and known_hosts must be readable" >&2; exit 1; }

command -v age >/dev/null || { echo "age is required" >&2; exit 1; }
command -v scp >/dev/null || { echo "scp is required" >&2; exit 1; }
command -v ssh >/dev/null || { echo "ssh is required" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum is required" >&2; exit 1; }

umask 077
backup_name="$(basename "$backup_dir")"
[[ "$backup_name" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "backup directory name contains unsafe characters" >&2; exit 1; }
output_file="$(mktemp -t "${backup_name}.XXXXXX.age")"
checksum_file="${output_file}.sha256"
encrypted_name="${backup_name}.tar.gz.age"
remote_file="$BACKUP_REMOTE_DIR/${backup_name}.tar.gz.age"
remote_checksum="$remote_file.sha256"
cleanup() { rm -f -- "$output_file" "$checksum_file"; }
trap cleanup EXIT

tar -C "$(dirname "$backup_dir")" -czf - "$backup_name" \
    | age -r "$AGE_RECIPIENT" -o "$output_file"
chmod 600 "$output_file"
(cd "$(dirname "$output_file")" && sha256sum "$(basename "$output_file")" \
    | sed "s#$(basename "$output_file")#$encrypted_name#") > "$checksum_file"
chmod 600 "$checksum_file"

ssh_opts=(
    -i "$BACKUP_SSH_KEY"
    -o IdentitiesOnly=yes
    -o StrictHostKeyChecking=yes
    -o UserKnownHostsFile="$BACKUP_KNOWN_HOSTS_FILE"
)
remote_target="$BACKUP_REMOTE_USER@$BACKUP_REMOTE_HOST"
ssh "${ssh_opts[@]}" "$remote_target" \
    "install -d -m 700 -- '$BACKUP_REMOTE_DIR' && test ! -e '$remote_file' && test ! -e '$remote_checksum'"
scp "${ssh_opts[@]}" "$output_file" "$remote_target:$remote_file"
scp "${ssh_opts[@]}" "$checksum_file" "$remote_target:$remote_checksum"
ssh "${ssh_opts[@]}" "$remote_target" \
    "chmod 600 -- '$remote_file' '$remote_checksum' && cd '$BACKUP_REMOTE_DIR' && sha256sum -c '$remote_checksum'"

printf 'Encrypted backup published: %s\n' "$remote_file"
