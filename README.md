# Scaccomatto Server — What Changed

*Account service updates · June 2026*

---

# Security

## Required database credentials
Server now reads credentials from environment variables and refuses to start if they are missing.

No silent fallback to an empty password.

---

## Admin role system
All sensitive operations now require admin status:

- Ban users
- Delete accounts
- List users
- Reset passwords

Non-admins receive:

```http
403 Forbidden
```

No public bootstrap endpoint exists.

The first admin must be promoted manually through SQL.

---

## Ban enforcement at login
Banned accounts are rejected immediately during authentication before any session is issued.

---

## Rate limiting on auth endpoints
Authentication endpoints are limited to:

- 5 attempts per minute per IP

Server returns:

```http
429 Too Many Requests
```

Stale entries are cleaned automatically.

---

# New Features

## Settings persistence
Game settings are now saved to user accounts and restored automatically at login.

Guest users still use temporary local preferences.

---

## Remember me
Long-lived authentication tokens (30 days) allow automatic login on future launches.

Security behavior:
- Tokens rotate on every use
- Tokens are deleted server-side on logout

---

## Log out all devices
New endpoint removes all active remember tokens across every device.

---

## Request logging
Every request is now logged with:

- HTTP method
- Endpoint path
- IP address
- Response status
- Response time

Log files rotate daily.

---

## Health endpoint

```http
GET /api/health
```

Returns server status information.

Useful for:
- Monitoring
- Uptime checks
- Load balancers

---

## Admin endpoints

```http
/api/admin/*
```

New administrative features:
- List users
- Delete accounts
- Ban players

All endpoints are role-checked through `AdminService`.

---

# Schema Migrations

## V3 — V5 added

New migrations include:
- Admin flags
- User settings column
- Remember tokens table

All migrations execute automatically on startup in filename order.
