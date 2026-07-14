# SMTP Email Setup Guide

The mailer in `utils/mailer.js` is **gracefully optional** — if `SMTP_HOST` is not set in `.env`, emails are skipped and a warning is logged. Nothing breaks. Add SMTP when you're ready to go live.

---

## Option A — Gmail (easiest for testing and small scale)

Gmail is free and works well for up to ~500 emails/day. For production at 5,000 users, use Option B.

### Step 1 — Enable 2-Factor Authentication on your Google account

1. Go to [myaccount.google.com](https://myaccount.google.com)
2. Click **Security** in the left sidebar
3. Under "How you sign in to Google", click **2-Step Verification**
4. Follow the steps to enable it (required before App Passwords work)

### Step 2 — Create an App Password

1. Still in Google Account → **Security**
2. Under "How you sign in to Google", click **App passwords**
   (If you don't see this option, 2FA is not enabled — go back to Step 1)
3. In the "Select app" dropdown choose **Mail**
4. In the "Select device" dropdown choose **Other (Custom name)**
5. Type `CAA Recruitment` and click **Generate**
6. Google shows a **16-character password** like `abcd efgh ijkl mnop`
   **Copy it immediately** — it's only shown once
7. Click Done

### Step 3 — Add to your `.env` file

```env
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_SECURE=false
SMTP_USER=your_gmail_address@gmail.com
SMTP_PASS=abcdefghijklmnop
SMTP_FROM="CAA Recruitment" <noreply@caa.go.ug>
```

> **Note:** `SMTP_PASS` is the 16-character App Password (no spaces), NOT your regular Gmail password.
> `SMTP_FROM` is the display name + email that recipients see. You can put any email here but deliverability is better if it matches `SMTP_USER`.

### Step 4 — Test it

Start your server and register a new user account. You should see a welcome email arrive.
If it doesn't arrive, check:
- Your spam folder
- The server console for `[mailer]` log lines

---

## Option B — Brevo (formerly Sendinblue) — Recommended for production

Brevo has a generous free tier (300 emails/day) and excellent deliverability. This is better for 5,000 users.

### Step 1 — Create a Brevo account

1. Go to [brevo.com](https://www.brevo.com) and sign up for free
2. Verify your email

### Step 2 — Verify your sending domain (recommended)

This step makes emails not go to spam:
1. In Brevo, go to **Settings → Senders & IP → Domains**
2. Click **Add a new domain**
3. Enter `caa.go.ug` (or your actual domain)
4. Brevo shows you DNS records to add — give these to whoever manages the `caa.go.ug` DNS (likely your IT team)
5. Once DNS propagates (up to 48h), the domain shows as verified

### Step 3 — Get SMTP credentials

1. In Brevo, go to **Settings → SMTP & API → SMTP**
2. Note down:
   - SMTP Server: `smtp-relay.brevo.com`
   - Port: `587`
   - Login: your Brevo account email
   - Password: the **SMTP key** shown on the page (starts with `xkeysib-`)

### Step 4 — Add to your `.env` file

```env
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_SECURE=false
SMTP_USER=your_brevo_login@email.com
SMTP_PASS=xkeysib-xxxxxxxxxxxxxxxxxxxx
SMTP_FROM="CAA Uganda Recruitment" <recruitment@caa.go.ug>
```

---

## Option C — Mailtrap (safe local testing — emails never delivered to real inboxes)

Use this during development so you never accidentally spam real people.

### Step 1 — Create a Mailtrap account

1. Go to [mailtrap.io](https://mailtrap.io) and sign up free
2. Go to **Email Testing → Inboxes**
3. Click on your default inbox (or create one named `caa-recruitment-dev`)
4. Click **Show Credentials**

### Step 2 — Add to your `.env` file

```env
SMTP_HOST=sandbox.smtp.mailtrap.io
SMTP_PORT=2525
SMTP_SECURE=false
SMTP_USER=<username from mailtrap>
SMTP_PASS=<password from mailtrap>
SMTP_FROM="CAA Recruitment Dev" <dev@caa.local>
```

All sent emails will appear in your Mailtrap inbox — safe to test without sending to real users.

---

## Email Templates

The mailer sends three types of emails automatically:

| Trigger | Template | Sent to |
|---------|----------|---------|
| New user registers | Welcome email | New user |
| Admin updates application status (with notifyEmail) | Status update (branded HTML) | Candidate |
| `POST /api/emails` | Custom message | Specified recipient |
| `POST /api/emails/bulk` | Custom message per recipient | Each recipient |

### Customising Templates

Edit `utils/mailer.js`. Templates are in the functions:
- `welcomeEmail()` — registration welcome
- `applicationStatusEmail()` — status update with colored badge
- `bulkEmail()` — generic HR message

The `wrap()` function at the top provides the branded HTML shell (blue CAA header, white body, grey footer). Edit the CSS inside `wrap()` to match your branding.

---

## Troubleshooting

| Problem | Likely cause | Fix |
|---------|-------------|-----|
| Emails not sending | `SMTP_HOST` not set | Add SMTP vars to `.env` and restart server |
| `[mailer] ... failed: Invalid login` | Wrong credentials | Re-check SMTP_USER and SMTP_PASS |
| `[mailer] ... failed: ECONNREFUSED` | Wrong host/port | Check SMTP_HOST and SMTP_PORT |
| Emails going to spam | Unverified domain | Verify your sending domain with Brevo |
| Gmail `Username and Password not accepted` | Using normal Gmail password | Must use App Password (16 chars, no spaces) |
| Gmail `Less secure app access` errors | 2FA not enabled | Enable 2FA first, then create App Password |
