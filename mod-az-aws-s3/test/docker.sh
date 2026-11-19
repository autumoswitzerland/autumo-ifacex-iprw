#!/usr/bin/env bash

# Start/stop the local S3 emulator (Adobe S3Mock) used by AWSS3IntegrationTest.
#
# Usage:
#   ./docker.sh start   # start S3Mock (detached)
#   ./docker.sh stop    # stop and remove the container
#   ./docker.sh logs    # follow the container logs
#
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

case "${1:-start}" in
	start)
		docker compose -f "$DIR/compose.yaml" up -d
		cat <<'EOF'

S3Mock started:
  endpoint : http://localhost:9090

Add to bin/.env:
  AWS_TEST_EMULATOR=1
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
