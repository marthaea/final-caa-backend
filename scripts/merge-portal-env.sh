#!/usr/bin/env bash
# Set Netlify portal URL + secure cookies in .env (no SMTP secrets). See README / runbook.
set -euo pipefail
ENV_FILE="${1:-.env}"
FRONTEND_URL="${FRONTEND_URL:-https://recruitfront.netlify.app}"
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-https://recruitfront.netlify.app}"
export FRONTEND_URL CORS_ALLOWED_ORIGINS

python3 - "$ENV_FILE" <<PY
from pathlib import Path
import os, sys
path = Path(sys.argv[1])
lines = path.read_text().splitlines() if path.exists() else []
updates = {
    "FRONTEND_URL": os.environ["FRONTEND_URL"],
    "CORS_ALLOWED_ORIGINS": os.environ["CORS_ALLOWED_ORIGINS"],
    "AUTH_COOKIE_SECURE": "true",
    "AUTH_COOKIE_SAME_SITE": "None",
    "SPRING_PROFILES_ACTIVE": "prod",
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
print(f"Updated {path}. Recreate API:")
print("  docker compose -f docker-compose.prod.yml up -d api --no-deps --force-recreate")
PY
