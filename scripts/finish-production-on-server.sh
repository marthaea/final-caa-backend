#!/usr/bin/env bash
# Run on Thewton-Server: /opt/caa-recruitment/scripts/finish-production-on-server.sh
# Requires Brevo creds in the environment for the SMTP step (do not commit).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ENV_FILE="${1:-$ROOT/.env}"

echo "==> Portal URL + secure cookies"
"$ROOT/scripts/merge-portal-env.sh" "$ENV_FILE"

if [[ -n "${SMTP_USER:-}" && -n "${SMTP_PASSWORD:-}" && -n "${SMTP_FROM:-}" ]]; then
  echo "==> Brevo SMTP"
  "$ROOT/scripts/configure-brevo-env.sh" "$ENV_FILE"
else
  echo "==> Skipping SMTP (set SMTP_USER, SMTP_PASSWORD, SMTP_FROM and re-run, or run configure-brevo-env.sh)"
fi

echo "==> Recreate API container"
docker compose -f docker-compose.prod.yml up -d api --no-deps --force-recreate

echo "==> Wait for health"
sleep 20
docker exec caa-api wget -qO- http://127.0.0.1:8080/ping >/dev/null

echo "==> Effective env (no secrets)"
docker exec caa-api printenv SMTP_ENABLED SMTP_PORT SMTP_FROM FRONTEND_URL SPRING_PROFILES_ACTIVE CORS_ALLOWED_ORIGINS

PENDING="$(docker exec caa-postgres psql -U caa -d caa_recruitment -t -A -c \
  "SELECT count(*) FROM outbox_events WHERE event_type = 'identity.email-verification-requested' AND published_at IS NULL;")"
echo "==> Pending verification emails in outbox: $PENDING"
if [[ "$(docker exec caa-api printenv SMTP_ENABLED)" == "true" && "$PENDING" != "0" ]]; then
  echo "    Outbox worker should drain these within ~30s."
fi
echo "Done."
