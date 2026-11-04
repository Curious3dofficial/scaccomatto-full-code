#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
base_url="${ACCOUNT_BASE_URL:-http://127.0.0.1:8080}"
rm -rf /tmp/scaccomatto-account-client-test
mkdir -p /tmp/scaccomatto-account-client-test
javac \
  -d /tmp/scaccomatto-account-client-test \
  src/AccountJson.java \
  src/AccountApiClient.java \
  server/tests/AccountClientSmokeTest.java
java \
  -cp /tmp/scaccomatto-account-client-test \
  AccountClientSmokeTest \
  "$base_url" \
  "${ACCOUNT_OTP_OUTBOX:-server/data/otp-outbox.log}"
