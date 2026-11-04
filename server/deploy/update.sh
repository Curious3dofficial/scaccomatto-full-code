#!/usr/bin/env bash
# Ship a new server build to the VM (run on YOUR computer).
# Usage:  server/deploy/update.sh ubuntu@YOUR.VM.IP ~/.ssh/oracle_key your-domain
set -euo pipefail
HOST="${1:?ubuntu@ip}"; KEY="${2:?path to ssh key}"; DOMAIN="${3:?domain}"
cd "$(dirname "$0")"
bash package.sh
scp -i "$KEY" scaccomatto-server.tar.gz "$HOST":/tmp/
ssh -i "$KEY" "$HOST" "rm -rf /tmp/scc && mkdir /tmp/scc && tar -xzf /tmp/scaccomatto-server.tar.gz -C /tmp/scc && sudo bash /tmp/scc/server/deploy/setup.sh $DOMAIN"
