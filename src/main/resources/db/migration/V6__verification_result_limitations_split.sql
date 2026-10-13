ALTER TABLE verification_result_limitations
    ADD COLUMN IF NOT EXISTS position INTEGER;

WITH numbered AS (
    SELECT ctid,
           row_number() OVER (PARTITION BY result_id ORDER BY ctid) AS rn
    FROM verification_result_limitations
)
UPDATE verification_result_limitations AS l
SET position = n.rn
FROM numbered AS n
WHERE l.ctid = n.ctid;

ALTER TABLE verification_result_limitations
    ALTER COLUMN position SET NOT NULL;

CREATE TABLE IF NOT EXISTS verification_result_applicability_limitations (
    result_id UUID NOT NULL REFERENCES verification_results(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    text TEXT NOT NULL,
    PRIMARY KEY (result_id, position)
);

CREATE INDEX IF NOT EXISTS idx_verification_result_applicability_limitations_result
    ON verification_result_applicability_limitations (result_id, position);
