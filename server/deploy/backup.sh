#!/usr/bin/env bash
# Copies the account database to /opt/scaccomatto/backups, encrypted (run nightly by
# cron, as root). Works while the server is running.
#
# Backups are locked with the key in /etc/scaccomatto/backup.key. Keep a copy of that
# key somewhere safe OFF the server -- without it the backups can't be opened.
# To open one:
#   openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
#     -in accounts-YYYY-MM-DD.zip.enc -out accounts.zip -pass file:backup.key
set -euo pipefail
umask 077
set -a; . /etc/scaccomatto/account.env; set +a
KEY=/etc/scaccomatto/backup.key
DIR=/opt/scaccomatto/backups
STAMP=$(date +%F)
PLAIN="$DIR/.accounts-$STAMP.zip"
OUT="$DIR/accounts-$STAMP.zip.enc"
if [ ! -s "$KEY" ]; then
  echo "No backup key at $KEY (run setup.sh again to create one)" >&2
  exit 1
fi
rm -f "$PLAIN"
# the copy is made as the server's own user, so file ownership never changes
runuser -u scaccomatto -- java -cp '/opt/scaccomatto/app/server/lib/*' org.h2.tools.Shell \
  -url "${ACCOUNT_DB_URL};IFEXISTS=TRUE" -user "$ACCOUNT_DB_USER" -password "$ACCOUNT_DB_PASS" \
  -sql "BACKUP TO '$PLAIN'" >/dev/null
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -in "$PLAIN" -out "$OUT" -pass "file:$KEY"
shred -u "$PLAIN" 2>/dev/null || rm -f "$PLAIN"
chmod 600 "$OUT"
find "$DIR" \( -name 'accounts-*.zip.enc' -o -name 'accounts-*.zip' \) -mtime +14 -delete
echo "Backed up (encrypted) to $OUT"
