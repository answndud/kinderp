#!/usr/bin/env sh
set -eu
umask 077

: "${1:?usage: encrypt-backup.sh BACKUP_DIRECTORY OUTPUT_FILE}"
: "${2:?usage: encrypt-backup.sh BACKUP_DIRECTORY OUTPUT_FILE}"
backup_root="$1"
output_file="$2"
: "${AGE_RECIPIENT:?set AGE_RECIPIENT to an age public key}"

[ -d "$backup_root" ] || { echo "backup directory is missing: $backup_root" >&2; exit 1; }
command -v age >/dev/null || { echo "age is required" >&2; exit 1; }
case "$output_file" in
  /*) ;;
  *) echo "output file must be an absolute path" >&2; exit 1 ;;
esac
[ ! -e "$output_file" ] || { echo "refusing to overwrite existing output" >&2; exit 1; }

parent_dir=$(dirname "$backup_root")
backup_name=$(basename "$backup_root")
tar -C "$parent_dir" -czf - "$backup_name" | age -r "$AGE_RECIPIENT" -o "$output_file"
chmod 600 "$output_file"
echo "encrypted ERP backup created: $output_file"
