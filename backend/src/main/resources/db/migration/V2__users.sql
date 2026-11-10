CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_username_lowercase CHECK (username = LOWER(username)),
    CONSTRAINT ck_users_email_lowercase CHECK (email = LOWER(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);
