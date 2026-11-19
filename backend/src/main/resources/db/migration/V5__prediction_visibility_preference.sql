ALTER TABLE users
    ADD COLUMN hide_predictions_until_kickoff BOOLEAN NOT NULL DEFAULT TRUE;
