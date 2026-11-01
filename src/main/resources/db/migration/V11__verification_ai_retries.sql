CREATE TABLE verification_ai_retries (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES verification_runs(id) ON DELETE CASCADE,
    result_id UUID NOT NULL REFERENCES verification_results(id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES users(id),
    attempted_at TIMESTAMPTZ NOT NULL,
    ticket_snapshot JSONB NOT NULL,
    previous_result JSONB NOT NULL,
    previous_revision INT NOT NULL,
    previous_proposed_status result_status NOT NULL,
    previous_effective_status result_status NOT NULL,
    proposed_status result_status NOT NULL,
    effective_status result_status NOT NULL,
    applicability_decision applicability_decision NOT NULL,
    rationale TEXT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    model VARCHAR(128),
    response_id VARCHAR(255),
    CONSTRAINT verification_ai_retry_outcome CHECK (outcome IN ('APPLIED', 'HUMAN_PRESERVED', 'NO_CHANGE'))
);

CREATE INDEX verification_ai_retries_result_order
    ON verification_ai_retries(result_id, attempted_at DESC, id DESC);

CREATE OR REPLACE FUNCTION prevent_ai_retry_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'verification_ai_retries is append-only';
END;
$$;

CREATE TRIGGER verification_ai_retries_immutable
    BEFORE UPDATE OR DELETE ON verification_ai_retries
    FOR EACH ROW EXECUTE FUNCTION prevent_ai_retry_mutation();
