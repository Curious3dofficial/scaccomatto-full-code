# Putting the Scaccomatto account server online (Oracle Cloud, free)

What you'll end up with: a small always-on Linux computer at Oracle that runs the
account server, reachable at `https://YOUR-NAME.duckdns.org`, with a free https
certificate that renews itself, nightly database backups, and the app pointing at it.

Time: about 45 minutes the first time.

---

## 1. Get a free web address (5 min)

1. Go to **duckdns.org** and sign in (Google/GitHub is fine).
2. Type a name (for example `scaccomatto`) and press **add domain**.
   Your address is now `scaccomatto.duckdns.org`.
3. Leave the tab open. You'll paste the server's IP address there in step 3.

(If you buy a real domain later, just point it at the same IP and run setup again
with the new name.)

## 2. Create the free server (15 min)

1. Sign up at **cloud.oracle.com** (needs a card for identity checks; the
   *Always Free* resources below are not charged).
2. Menu → **Compute → Instances → Create instance**.
   - **Image:** Canonical **Ubuntu 24.04**.
   - **Shape:** *Ampere* → **VM.Standard.A1.Flex**, 1 OCPU, 6 GB memory
     (Always Free). If it says "out of capacity", try again later or pick
     **VM.Standard.E2.1.Micro** (also free).
   - **SSH keys:** choose **Generate a key pair** and **download the private key**.
     Save it as `~/.ssh/oracle_key`, then run `chmod 600 ~/.ssh/oracle_key`.
3. Press **Create**. When it's running, copy its **Public IP address**.
4. Open the doors for web traffic: on the instance page click the **subnet** link →
   **Security Lists** → the default list → **Add Ingress Rules**:
   - Source CIDR `0.0.0.0/0`, IP protocol **TCP**, destination port **80**
   - another rule, same, destination port **443**

## 3. Point your address at the server (1 min)

Back on duckdns.org, paste the server's public IP into the **current ip** box next
to your name and press **update ip**.

## 4. Install the server (10 min)

On **your computer**, in the `Scaccomatto` project folder:

```bash
bash server/deploy/update.sh ubuntu@YOUR.SERVER.IP ~/.ssh/oracle_key scaccomatto.duckdns.org
```

That builds the server, copies it up, and runs `setup.sh` there, which installs
Java and Caddy (the https part), creates the service, opens the firewall and sets
up backups. At the end it prints:

```
Server is running. Check from anywhere: https://scaccomatto.duckdns.org/api/health
```

Open that link in a browser. You should see `{"status":"ok"}`.
(The very first time, the certificate can take a minute; refresh if it errors.)

**Save your backup key now.** Every night the server makes a locked (encrypted) copy
of the accounts database. The key that opens those copies lives on the server; if the
server is ever lost, the key goes with it. Show it once and keep it in a password
manager or another safe place:

```bash
ssh -i ~/.ssh/oracle_key ubuntu@YOUR.SERVER.IP 'sudo cat /etc/scaccomatto/backup.key'
```

## 5. Point the app at it (1 min)

Edit `src/main/assets/account-server.txt` and put your address on its own line:

```
https://scaccomatto.duckdns.org
```

Rebuild the app. It now creates and signs in to accounts on your server, and
the TEST/DEV buttons are gone. To work on the app against a local server again,
start it with `-Dscaccomatto.dev=true -Dscaccomatto.accountApi=http://127.0.0.1:8080`.

---

## Optional: "Continue with Google"

1. In **console.cloud.google.com** → APIs & Services → **Credentials** →
   **Create credentials → OAuth client ID** → type **Web application**.
2. Under **Authorized redirect URIs** add
   `https://scaccomatto.duckdns.org/oauth/google/callback`.
3. Download the JSON and copy it to the server:
   ```bash
   scp -i ~/.ssh/oracle_key client_secret.json ubuntu@YOUR.SERVER.IP:/tmp/
   ssh -i ~/.ssh/oracle_key ubuntu@YOUR.SERVER.IP \
     'sudo mv /tmp/client_secret.json /opt/scaccomatto/credentials/ && sudo chown scaccomatto /opt/scaccomatto/credentials/client_secret.json && sudo chmod 600 /opt/scaccomatto/credentials/client_secret.json && sudo systemctl restart scaccomatto-account'
   ```
Without it, everything else works; Google sign-in just says it isn't set up.

**Important:** your project folder has old Google secrets in
`server/credentials/`. Delete that folder from the project (keep a copy somewhere
private if you need it), and if the project was ever shared or uploaded, press
**Reset secret** on that client in Google's console.

## Optional: real emails for username-change codes

Until this is set, the six-digit codes are written to a file on the server
(`/opt/scaccomatto/data/otp-outbox.log`), so players can't receive them.
A free option is **Brevo** (300 emails/day): create an SMTP key, then on the server

```bash
sudo nano /etc/scaccomatto/account.env      # fill in the SMTP_ lines, remove their #
sudo systemctl restart scaccomatto-account
```

---

## Everyday commands (on the server)

| What | Command |
| --- | --- |
| Is it running? | `systemctl status scaccomatto-account` |
| Recent server messages | `sudo journalctl -u scaccomatto-account -n 50` |
| Who's been connecting | `sudo tail /opt/scaccomatto/data/logs/access-*.log` |
| Restart | `sudo systemctl restart scaccomatto-account` |
| Back up right now | `sudo scaccomatto-backup` |
| Backups | `/opt/scaccomatto/backups/accounts-YYYY-MM-DD.zip.enc` (encrypted, 14 days kept) |

**Shipping a new server version:** run the same `update.sh` command from step 4.
Accounts and settings are kept.

**Make yourself an admin** (once you've created your account in the app):

```bash
sudo systemctl stop scaccomatto-account
sudo -u scaccomatto bash -c 'set -a; . /etc/scaccomatto/account.env; java -cp "/opt/scaccomatto/app/server/lib/*" org.h2.tools.Shell -url "$ACCOUNT_DB_URL" -user "$ACCOUNT_DB_USER" -password "$ACCOUNT_DB_PASS" -sql "UPDATE users SET is_admin = TRUE WHERE username_normalized = '"'"'yourusername'"'"'"'
sudo systemctl start scaccomatto-account
```

**Copy a backup to your computer** (do this now and then):

```bash
ssh -i ~/.ssh/oracle_key ubuntu@YOUR.SERVER.IP 'sudo cp /opt/scaccomatto/backups/*.enc /tmp/ && sudo chmod 644 /tmp/accounts-*.enc'
scp -i ~/.ssh/oracle_key 'ubuntu@YOUR.SERVER.IP:/tmp/accounts-*.enc' ~/scaccomatto-backups/
ssh -i ~/.ssh/oracle_key ubuntu@YOUR.SERVER.IP 'sudo rm /tmp/accounts-*.enc'
```

**Open a backup** (needs the key you saved, in a file called `backup.key`):

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in accounts-YYYY-MM-DD.zip.enc -out accounts.zip -pass file:backup.key
```

**Tell players about a new version:** on the server,

```bash
sudo nano /etc/scaccomatto/account.env    # set ACCOUNT_LATEST_VERSION=1.0.1 and ACCOUNT_DOWNLOAD_URL=https://...
sudo systemctl restart scaccomatto-account
```

Everyone running an older version sees a small "new version available" note on the
title screen, with a button that opens the download page.

---

## What the server does to stay safe

- Passwords are stored only as slow, salted hashes (PBKDF2, 210,000 rounds).
- One address can try 5 logins a minute and make 5 accounts an hour.
- An account that gets 10 wrong passwords in 15 minutes (from anywhere) is paused for
  15 minutes, so slow guessing from many machines doesn't work either.
- A wrong username takes as long to reject as a wrong password, so nobody can find
  out which usernames exist by timing the answers.
- Players can delete their own account (Settings > Privacy & Account); everything
  stored with it goes too.
- The server only listens on the machine itself; the outside world reaches it through
  https only. Its files and backups can be read by the server's own user and nobody
  else, and backups are encrypted.
