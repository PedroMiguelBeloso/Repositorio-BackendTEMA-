CREATE INDEX idx_verification_runs_history
    ON verification_runs (started_at DESC, id DESC);

CREATE INDEX idx_verification_runs_service_history
    ON verification_runs (service_id, started_at DESC, id DESC);
