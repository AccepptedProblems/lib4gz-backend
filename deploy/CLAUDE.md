# deploy

Owns: getting the backend onto Google Cloud Run, and running it locally with the real
secrets. Run nothing here unless the owner asks for a deploy.

## Facts

- Target: project `library4gz`, service `libforgzbe`, region `asia-southeast1`. `deploy.sh`
  builds with Cloud Build, pushes to Artifact Registry and deploys.
- Secrets come from Google Secret Manager at runtime: `SUPABASE_PROJECT_REF`,
  `SUPABASE_DB_PASSWORD`, `JWT_SECRET_KEY`, `BREVO_API_KEY`, `GOOGLE_CLIENT_ID`.
- `MAIL_PROVIDER`, `MAIL_FROM_NAME` and `MAIL_FROM_ADDRESS` are plain environment
  variables set by `deploy.sh`, with defaults `brevo`, `Lib4GZ`,
  `no-reply@library4gz.com`.
- `setup-secrets.sh` only adds versions. A secret that does not exist yet must be created
  first: `gcloud secrets create <NAME> --replication-policy=automatic --project=library4gz`.
- `run-local.sh [gradle task]` pulls the five secrets and runs `bootRun`. It needs
  `gcloud auth login` with access to the project. It does not pull `MAIL_PROVIDER`, so a
  local run prints one-time codes to the console unless the shell or a `.env` file sets it.
- A new secret must be added in three places: `setup-secrets.sh`, `--set-secrets` in
  `deploy.sh`, and the list in `run-local.sh`. Also add it to `../.env.example`.
- `deploy.sh` does not set `API_DOCS_ENABLED`, so production does not serve the API
  document. Keep it that way.
- This folder is left out of the Docker and Cloud Build contexts.
