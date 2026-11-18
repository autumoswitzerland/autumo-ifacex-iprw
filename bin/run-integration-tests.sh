#!/bin/sh
###############################################################################
#
#  autumo ifaceX IPRW - integration test runner.
#
#  Runs the JUnit integration tests (*IT.java / *IntegrationTest.java) of the
#  reader/writer modules against real services. Tests are skipped unless the
#  required FTP_TEST_*/AWS_TEST_*/... environment variables are set.
#
#  A local, non-committed .env file is loaded automatically if present
#  (repo root or bin/). Use bin/.env.example as a template.
#
#  Usage:
#    bin/run-integration-tests.sh                 # run all modules
#    bin/run-integration-tests.sh mod-ftp          # run a single module
#    bin/run-integration-tests.sh mod-ftp mod-sftp # run several modules
#
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

# Load local .env (not committed) if present.
for envfile in "$ROOT_DIR/.env" "$SCRIPT_DIR/.env"; do
    if [ -f "$envfile" ]; then
        echo "Loading environment from $envfile"
        set -a
        . "$envfile"
        set +a
        break
    fi
done

if [ "$#" -gt 0 ]; then
    MODULES="$*"
else
    MODULES=""
    for d in mod-*/; do
        if [ -d "$d/src/test/java" ]; then
            MODULES="$MODULES ${d%/}"
        fi
    done
fi

echo "Running integration tests for:$MODULES"
echo ""

mvn -pl "$(echo $MODULES | tr ' ' ',')" -am verify
