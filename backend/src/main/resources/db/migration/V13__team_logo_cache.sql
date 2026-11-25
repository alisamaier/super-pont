ALTER TABLE teams ADD COLUMN logo_hash VARCHAR(64);
ALTER TABLE teams ADD COLUMN logo_source_url VARCHAR(2048);
ALTER TABLE teams ADD COLUMN logo_credit VARCHAR(1000);
ALTER TABLE teams ADD COLUMN provider_team_id BIGINT;
ALTER TABLE teams ADD COLUMN logo_checked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE teams ADD COLUMN logo_retry_after TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX uq_teams_provider_numeric_id ON teams(provider, provider_team_id);
