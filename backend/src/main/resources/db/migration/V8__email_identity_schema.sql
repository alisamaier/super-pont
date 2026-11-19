-- V7 validates and normalizes every account before any constraint is changed.
ALTER TABLE users DROP CONSTRAINT uq_users_email;
ALTER TABLE users DROP CONSTRAINT ck_users_email_lowercase;
ALTER TABLE users ADD CONSTRAINT ck_users_email_normalized
    CHECK (email = LOWER(TRIM(email)) AND email <> '');
ALTER TABLE users ADD CONSTRAINT uq_users_email_normalized UNIQUE (email);
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE users DROP CONSTRAINT uq_users_username;
ALTER TABLE users DROP CONSTRAINT ck_users_username_lowercase;
ALTER TABLE users DROP COLUMN username;
