CREATE TABLE predictions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    match_id UUID NOT NULL REFERENCES football_matches (id),
    home_score INTEGER NOT NULL,
    away_score INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_predictions_user_match UNIQUE (user_id, match_id),
    CONSTRAINT ck_predictions_home_score CHECK (home_score BETWEEN 0 AND 99),
    CONSTRAINT ck_predictions_away_score CHECK (away_score BETWEEN 0 AND 99)
);

CREATE INDEX ix_predictions_match ON predictions (match_id);
