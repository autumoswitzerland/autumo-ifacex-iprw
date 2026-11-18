#!/bin/sh
###############################################################################
#
#  autumo ifaceX IPRW - module test runner.
#
#  Runs the JUnit regression tests of the reader/writer modules.
#
#  Usage:
#    bin/run-tests.sh                 # test every module that has src/test
#    bin/run-tests.sh mod-ftp         # test a single module
#    bin/run-tests.sh mod-ftp mod-sftp # test several modules
#
###############################################################################

set -e

# Resolve repo root from script location.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

# Determine modules to test.
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

echo "Testing modules:$MODULES"
echo ""

mvn -pl "$(echo $MODULES | tr ' ' ',')" -am clean test
