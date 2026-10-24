#!/bin/bash
# ============================================================
# One-time setup: Create secrets in Google Secret Manager
# Run this ONCE to store your secrets, then use deploy.sh
# ============================================================

set -e

# ---- CONFIGURE THESE ----
PROJECT_ID="library4gz"
# --------------------------

echo "==> Enabling Secret Manager API..."
gcloud services enable secretmanager.googleapis.com --project="$PROJECT_ID"

echo "==> Adding secret versions (you will be prompted for values)..."
# This script only adds versions. Create any secret that does not exist yet first, e.g.:
#   gcloud secrets create BREVO_API_KEY --replication-policy=automatic --project=library4gz

read -rp "Enter SUPABASE_PROJECT_REF: " SUPABASE_REF
echo -n "$SUPABASE_REF" | gcloud secrets versions add SUPABASE_PROJECT_REF --data-file=- --project="$PROJECT_ID"

read -rsp "Enter SUPABASE_DB_PASSWORD: " SUPABASE_PASS
echo
echo -n "$SUPABASE_PASS" | gcloud secrets versions add SUPABASE_DB_PASSWORD --data-file=- --project="$PROJECT_ID"

read -rsp "Enter JWT_SECRET_KEY: " JWT_KEY
echo
echo -n "$JWT_KEY" | gcloud secrets versions add JWT_SECRET_KEY --data-file=- --project="$PROJECT_ID"

# The address codes are sent from is not a secret: deploy.sh passes MAIL_FROM_ADDRESS,
# MAIL_FROM_NAME and MAIL_PROVIDER as plain environment variables. Only the key lives here.
read -rsp "Enter BREVO_API_KEY (SMTP & API > API keys in Brevo, not the SMTP key): " BREVO_KEY
echo
echo -n "$BREVO_KEY" | gcloud secrets versions add BREVO_API_KEY --data-file=- --project="$PROJECT_ID"

read -rp "Enter GOOGLE_CLIENT_ID (OAuth Web client id): " GOOGLE_ID
echo -n "$GOOGLE_ID" | gcloud secrets versions add GOOGLE_CLIENT_ID --data-file=- --project="$PROJECT_ID"

echo "==> Granting Cloud Run access to secrets..."
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

for SECRET in SUPABASE_PROJECT_REF SUPABASE_DB_PASSWORD JWT_SECRET_KEY BREVO_API_KEY GOOGLE_CLIENT_ID; do
  gcloud secrets add-iam-policy-binding "$SECRET" \
    --member="serviceAccount:${SA}" \
    --role="roles/secretmanager.secretAccessor" \
    --project="$PROJECT_ID"
done

echo "==> Done! Secrets created and permissions granted."
echo "    Now run: ./deploy/deploy.sh"
