#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
server/scripts/bootstrap.sh
rm -rf server/out
mkdir -p server/out
javac -cp 'server/lib/*' -d server/out $(find server/src -name '*.java' -print)
