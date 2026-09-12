#!/usr/bin/env bash
# Merge Brevo SMTP settings into an .env file (does not print secrets).
set -euo pipefail

ENV_FILE="${1:-.env}"
SMTP_USER="${SMTP_USER:?Set SMTP_USER (Brevo SMTP login)}"
SMTP_PASSWORD="${SMTP_PASSWORD:?Set SMTP_PASSWORD (Brevo SMTP key)}"
SMTP_FROM="${SMTP_FROM:?Set SMTP_FROM (verified sender in Brevo)}"
SMTP_SENDER_NAME="${SMTP_SENDER_NAME-CAA HR Team}"

export SMTP_USER SMTP_PASSWORD SMTP_FROM SMTP_SENDER_NAME

python3 - "$ENV_FILE" <<'PY'
from pathlib import Path
import os
import sys

path = Path(sys.argv[1])
lines = path.read_text().splitlines() if path.exists() else []
updates = {
    "SMTP_ENABLED": "true",
    "SMTP_HOST": "smtp-relay.brevo.com",
    "SMTP_PORT": "2525",
    "SMTP_AUTH": "true",
    "SMTP_STARTTLS": "true",
    "SMTP_USER": os.environ["SMTP_USER"],
    "SMTP_PASSWORD": os.environ["SMTP_PASSWORD"],
    "SMTP_FROM": os.environ["SMTP_FROM"],
    "SMTP_SENDER_NAME": os.environ.get("SMTP_SENDER_NAME") or "CAA HR Team",
}
out, seen = [], set()
for line in lines:
    if not line.strip() or line.strip().startswith("#"):
        out.append(line)
        continue
    key = line.split("=", 1)[0]
    if key in updates:
        out.append(f"{key}={updates[key]}")
        seen.add(key)
    else:
        out.append(line)
for k, v in updates.items():
    if k not in seen:
        out.append(f"{k}={v}")
path.write_text("\n".join(out) + "\n")
print(f"Updated {path} (Brevo SMTP enabled). Restart API: docker compose up -d api")
PY
