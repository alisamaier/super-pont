ALTER TABLE users ALTER COLUMN hide_predictions_until_kickoff SET DEFAULT FALSE;
ALTER TABLE users ADD COLUMN password_version BIGINT NOT NULL DEFAULT 0;
