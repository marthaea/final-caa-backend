#!/usr/bin/env bash
# Idempotent database seed for the CAA Java backend.
# Usage:
#   ./scripts/seed.sh core
#   ./scripts/seed.sh demo
#   ./scripts/seed.sh volume
#   ./scripts/seed.sh all
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-}"
if [[ -z "$MODE" ]]; then
  echo "Usage: $0 {core|demo|volume|all}" >&2
  exit 1
fi

export SEED_MODE="$MODE"
# Passworded demo/bootstrap accounts are blocked unless SEED_DEMO=true.
export SEED_DEMO="${SEED_DEMO:-true}"
if [[ "$MODE" == "volume" || "$MODE" == "all" ]]; then
  export SEED_DEMO_VOLUME="${SEED_DEMO_VOLUME:-true}"
fi

# Never allow accidental production seeding from these helpers.
if [[ "${SPRING_PROFILES_ACTIVE:-dev}" == "prod" && "${SEED_ALLOW_PRODUCTION:-false}" != "true" ]]; then
  echo "Refusing to seed with SPRING_PROFILES_ACTIVE=prod" >&2
  exit 1
fi

cd "$ROOT"
exec ./mvnw -q -DskipTests spring-boot:run \
  -Dspring-boot.run.arguments="--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev},seed --server.port=0"
