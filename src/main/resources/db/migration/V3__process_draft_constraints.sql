-- WBS 5.1.2: constraints that protect process draft invariants at database level.

-- Two concurrent requests must not create the same official version for one process.
CREATE UNIQUE INDEX uq_process_versions_process_official_version
    ON process_versions (process_id, official_version);

ALTER TABLE process_versions
    ALTER COLUMN revision SET DEFAULT 0;

ALTER TABLE process_versions
    ADD CONSTRAINT chk_process_versions_revision_non_negative
    CHECK (revision >= 0);

ALTER TABLE process_policies
    ADD CONSTRAINT chk_process_policies_order_non_negative
    CHECK ("order" >= 0),
    ADD CONSTRAINT chk_process_policies_text_not_blank
    CHECK (btrim(text) <> '');

ALTER TABLE macro_activities
    ADD CONSTRAINT chk_macro_activities_order_non_negative
    CHECK ("order" >= 0),
    ADD CONSTRAINT chk_macro_activities_name_not_blank
    CHECK (btrim(name) <> '');

ALTER TABLE activities
    ADD CONSTRAINT chk_activities_order_non_negative
    CHECK ("order" >= 0),
    ADD CONSTRAINT chk_activities_name_not_blank
    CHECK (btrim(name) <> ''),
    ADD CONSTRAINT chk_activities_instructions_not_blank
    CHECK (btrim(instructions) <> '');
