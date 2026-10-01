-- OtpPurpose.EMAIL_CHANGE was renamed EMAIL_VERIFICATION. Written to be safe on a database where
-- the constraint was already changed by hand.

-- Codes live ten minutes, and Hibernate can no longer read the old value back into the enum.
DELETE FROM email_otps WHERE purpose = 'EMAIL_CHANGE';

ALTER TABLE email_otps DROP CONSTRAINT IF EXISTS email_otps_purpose_check;
ALTER TABLE email_otps ADD CONSTRAINT email_otps_purpose_check
    CHECK (purpose IN ('REGISTER', 'EMAIL_VERIFICATION', 'LOGIN', 'PASSWORD_RESET'));
