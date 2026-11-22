-- Deliberately preserve every pre-existing account as legacy verified.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;

CREATE TABLE registration_verifications (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    code_hash VARCHAR(60),
    code_expires_at TIMESTAMP WITH TIME ZONE,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    resend_available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    send_window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sends_in_window INTEGER NOT NULL DEFAULT 0 CHECK (sends_in_window >= 0)
);
