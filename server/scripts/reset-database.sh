#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
rm -f \
  server/data/scaccomatto.mv.db \
  server/data/scaccomatto.trace.db \
  server/data/otp-outbox.log
printf 'Local account database reset.\n'
