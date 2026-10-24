#!/bin/bash
# ============================================================
# Run the backend locally with secrets pulled from Google Secret
# Manager, so no .env file is needed.
#
#   ./deploy/run-local.sh              # bootRun
#   ./deploy/run-local.sh test         # any other gradle task
#
# Needs: gcloud login with access to project library4gz
# (gcloud auth login). Values already set in the shell win.
#
# MAIL_PROVIDER is deliberately not pulled: a local run logs one-time codes to the
# console. To send for real, run with MAIL_PROVIDER=brevo MAIL_FROM_ADDRESS=<verified sender>.
# ============================================================

set -e

PROJECT_ID="library4gz"
SECRETS=(SUPABASE_PROJECT_REF SUPABASE_DB_PASSWORD JWT_SECRET_KEY BREVO_API_KEY GOOGLE_CLIENT_ID)

cd "$(dirname "$0")/.."

for NAME in "${SECRETS[@]}"; do
  if [ -n "${!NAME}" ]; then
    echo "==> $NAME: using value from shell"
    continue
  fi
  if VALUE=$(gcloud secrets versions access latest --secret="$NAME" --project="$PROJECT_ID" 2>/dev/null); then
    export "$NAME=$VALUE"
    echo "==> $NAME: loaded from Secret Manager"
  else
    echo "==> $NAME: not found in Secret Manager, left unset"
  fi
done

# A local run serves the generated API reference (/api/swagger-ui.html); production never sets this.
export API_DOCS_ENABLED="${API_DOCS_ENABLED:-true}"

exec ./gradlew "${@:-bootRun}"
