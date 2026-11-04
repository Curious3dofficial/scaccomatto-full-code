#!/usr/bin/env bash
set -euo pipefail

base_url="${ACCOUNT_BASE_URL:-http://127.0.0.1:8080}"
suffix="$(date +%s)${RANDOM}"
username="test${suffix}"
email="${username}@example.com"
password="CorrectHorse-${suffix}"
new_username="renamed${suffix}"
outbox="${ACCOUNT_OTP_OUTBOX:-server/data/otp-outbox.log}"

health="$(curl --fail --silent "${base_url}/api/health")"
printf '%s\n' "$health" | grep -q '"status":"ok"'

register="$(
  curl --fail --silent \
    -H 'Content-Type: application/json' \
    -d "{\"profileName\":\"Smoke Player\",\"username\":\"${username}\",\"email\":\"${email}\",\"password\":\"${password}\"}" \
    "${base_url}/api/auth/register"
)"
token="$(printf '%s' "$register" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
test -n "$token"

profile="$(
  curl --fail --silent \
    -H "Authorization: Bearer ${token}" \
    "${base_url}/api/account/me"
)"
printf '%s\n' "$profile" | grep -q "\"username\":\"${username}\""

updated="$(
  curl --fail --silent \
    -X PATCH \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${token}" \
    -d '{"profileName":"Smoke Test Player"}' \
    "${base_url}/api/account/me"
)"
printf '%s\n' "$updated" | grep -q '"profileName":"Smoke Test Player"'

requested="$(
  curl --fail --silent \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${token}" \
    -d "{\"newUsername\":\"${new_username}\"}" \
    "${base_url}/api/account/username/request"
)"
printf '%s\n' "$requested" | grep -q '"status":"otp_sent"'

unchanged="$(
  curl --fail --silent \
    -H "Authorization: Bearer ${token}" \
    "${base_url}/api/account/me"
)"
printf '%s\n' "$unchanged" | grep -q "\"username\":\"${username}\""

code="$(
  grep "newUsername=${new_username}" "$outbox" \
    | tail -n 1 \
    | sed -n 's/.*code=\([0-9][0-9]*\).*/\1/p'
)"
test "${#code}" -eq 6

renamed="$(
  curl --fail --silent \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${token}" \
    -d "{\"code\":\"${code}\"}" \
    "${base_url}/api/account/username/verify"
)"
printf '%s\n' "$renamed" | grep -q "\"username\":\"${new_username}\""

curl --fail --silent \
  -X POST \
  -H "Authorization: Bearer ${token}" \
  "${base_url}/api/auth/logout" >/dev/null

login="$(
  curl --fail --silent \
    -H 'Content-Type: application/json' \
    -d "{\"login\":\"${new_username}\",\"password\":\"${password}\"}" \
    "${base_url}/api/auth/login"
)"
login_token="$(printf '%s' "$login" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
test -n "$login_token"

curl --fail --silent \
  -X POST \
  -H "Authorization: Bearer ${login_token}" \
  "${base_url}/api/auth/logout" >/dev/null

printf 'Account API smoke test passed for %s -> %s\n' "$username" "$new_username"
