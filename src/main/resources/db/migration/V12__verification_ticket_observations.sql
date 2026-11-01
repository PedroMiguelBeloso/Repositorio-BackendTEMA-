CREATE TABLE verification_result_ticket_observations (
    result_id UUID PRIMARY KEY REFERENCES verification_results(id) ON DELETE CASCADE,
    run_id UUID NOT NULL REFERENCES verification_runs(id) ON DELETE CASCADE,
    observed_at TIMESTAMPTZ NOT NULL,
    ticket_snapshot JSONB NOT NULL
);

CREATE INDEX verification_result_ticket_observations_run
    ON verification_result_ticket_observations(run_id);

CREATE TRIGGER verification_result_ticket_observations_immutable
    BEFORE UPDATE OR DELETE ON verification_result_ticket_observations
    FOR EACH ROW EXECUTE FUNCTION prevent_ai_retry_mutation();
