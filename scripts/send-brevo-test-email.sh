#!/usr/bin/env bash
# Send one test message through the production outbox → Brevo.
# Run on Thewton-Server: ./scripts/send-brevo-test-email.sh [recipient]
set -euo pipefail
TO="${1:-matthewkesh950@gmail.com}"
API="${API_BASE:-http://127.0.0.1:8082}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@caa.go.ug}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@2026}"

TOKEN="$(curl -sS -X POST "$API/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c "import sys,json; r=json.load(sys.stdin); assert r.get('success'), r; print(r['data']['token'])")"

curl -sS -X POST "$API/api/emails" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"to\":\"$TO\",\"candidateName\":\"Matthew\",\"subject\":\"CAA recruitment – Brevo test\",\"body\":\"<p>Test from Thewton-Server API. If you received this, Brevo SMTP is working.</p>\",\"trigger\":\"brevo-test\",\"jobTitle\":\"SMTP verification\"}"

echo
echo "Queued. Waiting for outbox worker…"
sleep 10
docker exec caa-postgres psql -U caa -d caa_recruitment -c \
  "SELECT id, event_type, published_at IS NOT NULL AS sent, left(coalesce(last_error,''),100) AS err
   FROM outbox_events WHERE payload::text ILIKE '%$TO%' ORDER BY id DESC LIMIT 3;"
