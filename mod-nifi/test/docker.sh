#!/usr/bin/env bash

# Recreate the NiFi test container so changed environment settings take effect.
# The named volumes (flow, certificates, truststore) are kept on purpose.
set -euo pipefail

docker compose up -d --force-recreate --remove-orphans
