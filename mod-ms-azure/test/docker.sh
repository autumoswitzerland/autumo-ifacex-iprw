#!/usr/bin/env bash

# Start/stop the local Azurite Azure Blob emulator used by
# AzureBlobStorageIntegrationTest.
#
# Usage:
#   ./docker.sh start   # start Azurite (detached)
#   ./docker.sh stop    # stop and remove the container
#   ./docker.sh logs    # follow the container logs
#
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

case "${1:-start}" in
	start)
		docker compose -f "$DIR/compose.yaml" up -d
		cat <<'EOF'

Azurite started:
  blob endpoint : http://127.0.0.1:10000/devstoreaccount1
  account       : devstoreaccount1

Add to bin/.env:
  AZURE_TEST_EMULATOR=1
EOF
		;;
	stop)
		docker compose -f "$DIR/compose.yaml" down
		;;
	logs)
		docker compose -f "$DIR/compose.yaml" logs -f
		;;
	*)
		echo "Usage: $0 [start|stop|logs]" >&2
		exit 1
		;;
esac
