#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
server/scripts/build.sh
exec java -cp 'server/out:server/lib/*' com.scaccomatto.account.AccountServer
