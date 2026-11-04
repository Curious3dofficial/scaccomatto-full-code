#!/usr/bin/env bash
# Builds the account server and packs it into server/deploy/scaccomatto-server.tar.gz
# (run on YOUR computer, from anywhere).
set -euo pipefail
cd "$(dirname "$0")/../.."                       # the Scaccomatto project folder
rm -rf server/out && mkdir -p server/out
javac --release 21 -nowarn -cp 'server/lib/*' -d server/out $(find server/src -name '*.java')
tar -czf server/deploy/scaccomatto-server.tar.gz \
    server/out server/lib server/db/migration \
    server/data/scaccomatto_elo_system.json server/data/scaccomatto_elo_changes.json \
    server/deploy/setup.sh server/deploy/backup.sh
echo "Packed: server/deploy/scaccomatto-server.tar.gz"
