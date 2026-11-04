#!/usr/bin/env bash
# One-time (and repeat-safe) setup on the Oracle Cloud VM (Ubuntu 22.04/24.04).
# Usage:   sudo bash setup.sh your-domain.example
# Run from the folder where you unpacked scaccomatto-server.tar.gz.
set -euo pipefail
DOMAIN="${1:?Usage: sudo bash setup.sh your-domain}"
APP=/opt/scaccomatto
SRC="$(cd "$(dirname "$0")/../.." && pwd)"     # the unpacked project root

echo "== 1/7 Packages (Java 21, Caddy for https)"
apt-get update -y
apt-get install -y openjdk-21-jre-headless curl debian-keyring debian-archive-keyring apt-transport-https gnupg iptables-persistent openssl
if ! command -v caddy >/dev/null; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y && apt-get install -y caddy
fi

echo "== 2/7 User and folders"
id scaccomatto >/dev/null 2>&1 || useradd --system --home "$APP" --shell /usr/sbin/nologin scaccomatto
mkdir -p "$APP/app" "$APP/data/logs" "$APP/credentials" "$APP/backups" /etc/scaccomatto
rm -rf "$APP/app/server"
cp -r "$SRC/server" "$APP/app/server"
chown -R scaccomatto:scaccomatto "$APP"
# only the server's own user can look inside its data, logs, sign-in secrets and backups
chmod 700 "$APP/data" "$APP/data/logs" "$APP/credentials" "$APP/backups"
find "$APP/data" "$APP/credentials" "$APP/backups" -type f -exec chmod 600 {} +

echo "== 3/7 Settings (/etc/scaccomatto/account.env)"
if [ ! -f /etc/scaccomatto/account.env ]; then
  DBPASS="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)"
  cat > /etc/scaccomatto/account.env <<ENV
# Scaccomatto account server settings. Restart after editing:
#   sudo systemctl restart scaccomatto-account
ACCOUNT_PORT=8080
ACCOUNT_BIND=127.0.0.1
ACCOUNT_TRUST_PROXY=true
ACCOUNT_DB_URL="jdbc:h2:file:$APP/data/scaccomatto;AUTO_SERVER=TRUE"
ACCOUNT_DB_USER=scaccomatto
ACCOUNT_DB_PASS=$DBPASS
ACCOUNT_LOG_FILE=$APP/data/logs/access.log
ACCOUNT_OTP_OUTBOX=$APP/data/otp-outbox.log
OAUTH_REDIRECT_BASE_URL=https://$DOMAIN
GOOGLE_OAUTH_REDIRECT_BASE_URL=https://$DOMAIN
GOOGLE_OAUTH_CREDENTIALS_PATH=$APP/credentials/client_secret.json
# Email for username-change codes (until set, codes go to otp-outbox.log on the server):
#SMTP_HOST=smtp-relay.brevo.com
#SMTP_PORT=465
#SMTP_USERNAME=
#SMTP_PASSWORD=
#SMTP_FROM=
ENV
  chmod 600 /etc/scaccomatto/account.env
fi
chmod 700 /etc/scaccomatto
# the key that locks the nightly backups (made once; keep a copy off the server!)
if [ ! -s /etc/scaccomatto/backup.key ]; then
  head -c 48 /dev/urandom | base64 | tr -d '\n' > /etc/scaccomatto/backup.key
  chmod 600 /etc/scaccomatto/backup.key
fi
# newer settings, added to an existing settings file if missing
grep -q '^ACCOUNT_LATEST_VERSION=' /etc/scaccomatto/account.env || cat >> /etc/scaccomatto/account.env <<ENV
# Newest app version and its download page, shown to players as "new version available":
ACCOUNT_LATEST_VERSION=
ACCOUNT_DOWNLOAD_URL=
ENV

echo "== 4/7 Service (starts on boot, restarts if it crashes)"
cat > /etc/systemd/system/scaccomatto-account.service <<UNIT
[Unit]
Description=Scaccomatto account server
After=network-online.target
Wants=network-online.target

[Service]
User=scaccomatto
WorkingDirectory=$APP/app
EnvironmentFile=/etc/scaccomatto/account.env
ExecStart=/usr/bin/java -Xmx256m -cp $APP/app/server/out:$APP/app/server/lib/* com.scaccomatto.account.AccountServer
Restart=always
RestartSec=5
# files it creates (database, logs, codes) are readable by this user only
UMask=0077
NoNewPrivileges=true
ProtectSystem=full
ProtectHome=true
PrivateTmp=true
PrivateDevices=true
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictSUIDSGID=true
LockPersonality=true

[Install]
WantedBy=multi-user.target
UNIT

echo "== 5/7 https (Caddy gets and renews the certificate by itself)"
cat > /etc/caddy/Caddyfile <<CADDY
$DOMAIN {
    encode gzip
    reverse_proxy 127.0.0.1:8080
    header {
        Strict-Transport-Security "max-age=31536000"
        X-Content-Type-Options nosniff
        -Server
    }
}
CADDY

echo "== 6/7 Firewall (Oracle's Ubuntu image blocks 80/443 by default)"
for port in 80 443; do
  iptables -C INPUT -p tcp --dport $port -m state --state NEW -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 5 -p tcp --dport $port -m state --state NEW -j ACCEPT
done
netfilter-persistent save

echo "== 7/7 Nightly encrypted backup (keeps 14 days)"
install -m 755 "$SRC/server/deploy/backup.sh" /usr/local/bin/scaccomatto-backup
echo "17 3 * * * root /usr/local/bin/scaccomatto-backup" > /etc/cron.d/scaccomatto-backup

systemctl daemon-reload
systemctl enable --now scaccomatto-account
systemctl restart scaccomatto-account
systemctl reload caddy || systemctl restart caddy
sleep 6
if curl -fsS http://127.0.0.1:8080/api/health >/dev/null; then
  echo; echo "Server is running. Check from anywhere: https://$DOMAIN/api/health"
  echo
  echo "IMPORTANT: copy your backup key somewhere safe (a password manager is ideal)."
  echo "Without it the backups can't be opened. Show it with:  sudo cat /etc/scaccomatto/backup.key"
else
  echo; echo "Server didn't answer yet. See: sudo journalctl -u scaccomatto-account -n 50"
fi
