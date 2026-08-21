#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
backup_output="$($script_dir/backup-netcup.sh)"
backup_dir="$(sed -n 's/^Netcup backup created: \([^ ]*\).*/\1/p' <<< "$backup_output")"
[[ -n "$backup_dir" ]] || { echo "could not resolve created backup directory" >&2; exit 1; }
printf '%s\n' "$backup_output"
"$script_dir/publish-encrypted-backup.sh" "$backup_dir"
