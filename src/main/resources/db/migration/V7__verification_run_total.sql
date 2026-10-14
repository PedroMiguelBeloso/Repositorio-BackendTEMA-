ALTER TABLE verification_runs
    ADD COLUMN total INT NOT NULL DEFAULT 0 CHECK (total >= 0);
