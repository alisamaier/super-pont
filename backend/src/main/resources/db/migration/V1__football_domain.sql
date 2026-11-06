CREATE TABLE countries (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    iso_code VARCHAR(2) NOT NULL UNIQUE,
    CONSTRAINT ck_country_code CHECK (CHAR_LENGTH(iso_code) = 2 AND iso_code = UPPER(iso_code))
);

CREATE TABLE competitions (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL REFERENCES countries(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    CONSTRAINT uq_competition_code UNIQUE (country_id, code)
);

CREATE TABLE teams (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL REFERENCES countries(id),
    name VARCHAR(120) NOT NULL,
    short_name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_team_name UNIQUE (country_id, name)
);

CREATE TABLE seasons (
    id UUID PRIMARY KEY,
    competition_id UUID NOT NULL REFERENCES competitions(id),
    name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    CONSTRAINT uq_season_name UNIQUE (competition_id, name),
    CONSTRAINT ck_season_dates CHECK (end_date >= start_date)
);

CREATE TABLE season_teams (
    season_id UUID NOT NULL REFERENCES seasons(id),
    team_id UUID NOT NULL REFERENCES teams(id),
    PRIMARY KEY (season_id, team_id)
);

CREATE TABLE competition_phases (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES seasons(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    format VARCHAR(20) NOT NULL,
    position INTEGER NOT NULL,
    win_points INTEGER NOT NULL,
    draw_points INTEGER NOT NULL,
    loss_points INTEGER NOT NULL,
    CONSTRAINT uq_phase_code UNIQUE (season_id, code),
    CONSTRAINT uq_phase_position UNIQUE (season_id, position),
    CONSTRAINT uq_phase_season UNIQUE (id, season_id),
    CONSTRAINT ck_phase_position CHECK (position > 0),
    CONSTRAINT ck_phase_points CHECK (win_points >= 0 AND draw_points >= 0 AND loss_points >= 0),
    CONSTRAINT ck_phase_format CHECK (format IN ('LEAGUE', 'KNOCKOUT'))
);

CREATE TABLE rounds (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES seasons(id),
    phase_id UUID NOT NULL,
    round_number INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT fk_round_phase FOREIGN KEY (phase_id, season_id) REFERENCES competition_phases(id, season_id),
    CONSTRAINT uq_round_number UNIQUE (phase_id, round_number),
    CONSTRAINT uq_round_season UNIQUE (id, season_id),
    CONSTRAINT ck_round_number CHECK (round_number > 0)
);

CREATE TABLE football_matches (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES seasons(id),
    round_id UUID NOT NULL,
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    kickoff TIMESTAMP WITH TIME ZONE NOT NULL,
    venue VARCHAR(160),
    status VARCHAR(20) NOT NULL,
    home_score INTEGER,
    away_score INTEGER,
    CONSTRAINT fk_match_round FOREIGN KEY (round_id, season_id) REFERENCES rounds(id, season_id),
    CONSTRAINT fk_match_home FOREIGN KEY (season_id, home_team_id) REFERENCES season_teams(season_id, team_id),
    CONSTRAINT fk_match_away FOREIGN KEY (season_id, away_team_id) REFERENCES season_teams(season_id, team_id),
    CONSTRAINT uq_match_fixture UNIQUE (round_id, home_team_id, away_team_id),
    CONSTRAINT ck_distinct_teams CHECK (home_team_id <> away_team_id),
    CONSTRAINT ck_match_status CHECK (status IN ('SCHEDULED', 'LIVE', 'FINISHED', 'POSTPONED', 'CANCELLED')),
    CONSTRAINT ck_score_pair CHECK ((home_score IS NULL AND away_score IS NULL) OR (home_score IS NOT NULL AND away_score IS NOT NULL)),
    CONSTRAINT ck_home_score CHECK (home_score >= 0),
    CONSTRAINT ck_away_score CHECK (away_score >= 0),
    CONSTRAINT ck_result_status CHECK (
        (status = 'FINISHED' AND home_score IS NOT NULL AND away_score IS NOT NULL)
        OR status = 'LIVE'
        OR (status IN ('SCHEDULED', 'POSTPONED', 'CANCELLED') AND home_score IS NULL AND away_score IS NULL)
    )
);

CREATE INDEX idx_team_country ON teams(country_id);
CREATE INDEX idx_season_team ON season_teams(team_id);
CREATE INDEX idx_round_season ON rounds(season_id);
CREATE INDEX idx_match_season_kickoff ON football_matches(season_id, kickoff);
CREATE INDEX idx_match_round ON football_matches(round_id);
