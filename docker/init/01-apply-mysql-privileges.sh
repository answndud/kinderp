#!/usr/bin/env bash
set -Eeuo pipefail

# The official MySQL image runs this only while initializing a new data volume.
# Existing volumes are reconciled by scripts/provision-mysql-privileges.sh.

MYSQL_CONTAINER=""
MYSQL_ROOT_USERNAME=root
export MYSQL_CONTAINER MYSQL_ROOT_USERNAME
exec /workspace/provision-mysql-privileges.sh
