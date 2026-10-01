-- The profile no longer changes the email by code: the address comes from the connected Google
-- account (POST /v1/auth/google:connect). Any change still pending at deploy time is dropped with
-- the column; nothing can finish it any more.
-- IF EXISTS: a database baselined at V1 is only assumed to match it, and this column was added by
-- ddl-auto=update, so it may never have existed there.
ALTER TABLE users DROP COLUMN IF EXISTS pending_email;

-- EMAIL_VERIFICATION codes are no longer issued. Codes live ten minutes.
DELETE FROM email_otps WHERE purpose = 'EMAIL_VERIFICATION';
ALTER TABLE email_otps DROP CONSTRAINT IF EXISTS email_otps_purpose_check;
ALTER TABLE email_otps ADD CONSTRAINT email_otps_purpose_check
    CHECK (purpose IN ('REGISTER', 'LOGIN', 'PASSWORD_RESET'));
