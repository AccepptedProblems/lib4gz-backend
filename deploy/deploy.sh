#!/bin/bash
# ============================================================
# Build and deploy to Google Cloud Run
# Secrets are pulled from Google Secret Manager at runtime
# ============================================================

set -e

# Cloud Build uploads the current directory and needs the Dockerfile in it: run from the repo root.
cd "$(dirname "$0")/.."

# ---- CONFIGURE THESE ----
PROJECT_ID="library4gz"
SERVICE_NAME="libforgzbe"
REGION="asia-southeast1"
REPO="lib4gz"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/lib4gz"
# --------------------------

echo "==> Ensuring Artifact Registry repo exists..."
gcloud artifacts repositories create "$REPO" \
  --repository-format=docker \
  --location="$REGION" \
  --project="$PROJECT_ID" 2>/dev/null || true

echo "==> Building and pushing image with Cloud Build..."
gcloud builds submit --tag "$IMAGE" --project="$PROJECT_ID"

echo "==> Deploying to Cloud Run..."
gcloud run deploy "$SERVICE_NAME" \
  --image "$IMAGE" \
  --platform managed \
  --region "$REGION" \
  --port 8080 \
  --allow-unauthenticated \
  --set-secrets "SUPABASE_PROJECT_REF=SUPABASE_PROJECT_REF:latest,SUPABASE_DB_PASSWORD=SUPABASE_DB_PASSWORD:latest,JWT_SECRET_KEY=JWT_SECRET_KEY:latest,BREVO_API_KEY=BREVO_API_KEY:latest,GOOGLE_CLIENT_ID=GOOGLE_CLIENT_ID:latest" \
  --set-env-vars "MAIL_PROVIDER=${MAIL_PROVIDER:-brevo},MAIL_FROM_NAME=${MAIL_FROM_NAME:-Lib4GZ},MAIL_FROM_ADDRESS=${MAIL_FROM_ADDRESS:-no-reply@library4gz.com}" \
  --memory 512Mi \
  --min-instances 0 \
  --max-instances 3 \
  --project="$PROJECT_ID"

echo "==> Deployment complete!"
gcloud run services describe "$SERVICE_NAME" --region="$REGION" --project="$PROJECT_ID" --format='value(status.url)'
