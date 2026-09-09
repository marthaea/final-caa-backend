#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ $# -ne 1 ]]; then
  echo "Usage: import-postgres.sh EXPORT_DIRECTORY" >&2
  exit 2
fi
if [[ -z "${PGSERVICE:-}" ]]; then
  echo "PGSERVICE is required; do not put a password-bearing URI on argv." >&2
  exit 2
fi
if [[ -n "${PGPASSFILE:-}" ]]; then
  [[ -f "$PGPASSFILE" && ! -L "$PGPASSFILE" ]] || { echo "PGPASSFILE must be a regular non-symlink file." >&2; exit 2; }
  [[ "$(stat -c '%a' "$PGPASSFILE")" =~ ^[46]00$ ]] || { echo "PGPASSFILE must have mode 0600 or 0400." >&2; exit 2; }
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
export_dir="$(cd -- "$1" && pwd -P)"
staging_sql="$script_dir/../postgres/create-staging.sql"
[[ "$export_dir" != *"'"* && "$export_dir" != *$'\n'* && "$staging_sql" != *"'"* ]] || {
  echo "Paths containing quotes or newlines are not supported." >&2
  exit 2
}

node "$script_dir/manifest.mjs" verify "$export_dir"
count_checks="$(node "$script_dir/manifest.mjs" sql-count-check "$export_dir")"

tables=(
  analytics_events users departments jobs applications assessments audit_log
  candidate_scores chatbot_queries criteria cv_profiles job_templates
  notifications permission_overrides sent_emails settings staff
)

{
  printf '%s\n' '\set ON_ERROR_STOP on' 'BEGIN;'
  printf "\\i '%s'\n" "$staging_sql"
  for table in "${tables[@]}"; do
    printf "\\copy migration_stage.%s FROM '%s/%s.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8');\n" \
      "$table" "$export_dir" "$table"
  done
  printf '%s\n' "$count_checks"
  printf '%s\n' 'COMMIT;'
} | psql -X -v ON_ERROR_STOP=1

echo "Imported 17 verified files into migration_stage only."
