CREATE TABLE team_logo_images (
    hash VARCHAR(64) PRIMARY KEY,
    png BYTEA NOT NULL,
    CONSTRAINT ck_team_logo_image_size CHECK (octet_length(png) BETWEEN 1 AND 2097152)
);
