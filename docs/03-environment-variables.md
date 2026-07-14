# Environment Variables Reference

All variables live in your `.env` file in the project root. Copy `.env.example` to `.env` and fill in the values. Never commit `.env` to git.

---

## Database

| Variable | Required | Example | Description |
|----------|----------|---------|-------------|
| `DB_HOST` | Yes | `localhost` | MySQL server hostname |
| `DB_USER` | Yes | `root` | MySQL username |
| `DB_PASSWORD` | Yes | `yourpassword` | MySQL password |
| `DB_NAME` | Yes | `caa-recruit` | Database name (note the hyphen) |
| `DB_PORT` | No | `3306` | MySQL port (default 3306) |

---

## JWT Authentication

| Variable | Required | Example | Description |
|----------|----------|---------|-------------|
| `JWT_SECRET` | Yes | *(64 random chars)* | Signs access tokens (2h). Must be long and random. |
| `JWT_EXPIRES_IN` | No | `2h` | Access token lifetime. Default: `2h` |
| `JWT_REFRESH_SECRET` | Yes | *(different 64 chars)* | Signs refresh tokens (7d). Must be different from JWT_SECRET. |
| `JWT_REFRESH_EXPIRES_IN` | No | `7d` | Refresh token lifetime. Default: `7d` |

### Generating secure secrets

Run this command twice (once for each secret — use different values):

```bash
node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
```

---

## Cloudinary (File Uploads)

Get these values from your [Cloudinary dashboard](https://cloudinary.com/console) → Settings → API Keys.

| Variable | Required | Example | Description |
|----------|----------|---------|-------------|
| `CLOUDINARY_CLOUD_NAME` | Yes | `my-cloud` | Your Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Yes | `123456789012345` | API key from Cloudinary dashboard |
| `CLOUDINARY_API_SECRET` | Yes | `xxxxxxxxxxxxxxxxxxx` | API secret — keep private |

---

## Email (SMTP)

Leave `SMTP_HOST` blank to disable email sending entirely (safe for early development).

| Variable | Required | Example | Description |
|----------|----------|---------|-------------|
| `SMTP_HOST` | No | `smtp.gmail.com` | SMTP server. Leave blank to disable email. |
| `SMTP_PORT` | No | `587` | SMTP port. 587 = TLS (recommended), 465 = SSL |
| `SMTP_SECURE` | No | `false` | Set to `true` only if using port 465 (SSL) |
| `SMTP_USER` | No | `you@gmail.com` | SMTP login username |
| `SMTP_PASS` | No | `abcdefghijklmnop` | SMTP password / App Password |
| `SMTP_FROM` | No | `"CAA Recruitment" <noreply@caa.co.ug>` | Display name and From address |

See `docs/02-smtp-setup.md` for provider-specific instructions.

---

## Server

| Variable | Required | Example | Description |
|----------|----------|---------|-------------|
| `PORT` | No | `5000` | Port the Express server listens on. Default: 5000 |
| `NODE_ENV` | No | `development` | Set to `production` in production. Controls: stack trace visibility, cookie security flag, Swagger UI availability |
| `FRONTEND_URL` | No | `http://localhost:3000` | Allowed CORS origin. Must exactly match your frontend URL (no trailing slash). |

---

## Complete `.env` template

```env
# ── Database ──────────────────────────────────────────────────────────────────
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=your_mysql_password_here
DB_NAME=caa-recruit
DB_PORT=3306

# ── JWT ───────────────────────────────────────────────────────────────────────
JWT_SECRET=paste_64_char_random_string_here
JWT_EXPIRES_IN=2h
JWT_REFRESH_SECRET=paste_different_64_char_random_string_here
JWT_REFRESH_EXPIRES_IN=7d

# ── Cloudinary ────────────────────────────────────────────────────────────────
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

# ── Email ─────────────────────────────────────────────────────────────────────
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_SECURE=false
SMTP_USER=your_email@gmail.com
SMTP_PASS=your_16char_app_password
SMTP_FROM="CAA Recruitment" <noreply@caa.co.ug>

# ── Server ────────────────────────────────────────────────────────────────────
PORT=5000
NODE_ENV=development
FRONTEND_URL=http://localhost:3000
```

---

## What Happens if Required Variables are Missing

The server validates `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `JWT_SECRET`, and `JWT_REFRESH_SECRET` on startup and exits immediately with a clear error message if any are missing:

```
[startup] Missing required env variables: JWT_REFRESH_SECRET
```

This prevents the server from starting in a broken state.
