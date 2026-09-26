CREATE TYPE user_role AS ENUM ('ADMIN', 'READER');
CREATE TYPE process_code AS ENUM ('START_UP', 'TAKE_OVER', 'SUPPORT_SOFTWARE_FACTORY', 'QUALITY_CONTINUOUS_IMPROVEMENT');
CREATE TYPE process_status AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');
CREATE TYPE service_type AS ENUM ('SUPPORT_ARIBA', 'SUPPORT_ERP', 'SOFTWARE_FACTORY', 'PROJECT_SAP_ARIBA', 'PROJECT_ERP');
CREATE TYPE service_status AS ENUM ('ACTIVE', 'INACTIVE');
CREATE TYPE evidence_type AS ENUM ('DOCUMENT', 'FOLDER', 'TICKET', 'IMPUTATIONS', 'TOOL_RECORD');
CREATE TYPE evidence_location AS ENUM ('INPUT', 'ACTIVITY_RECORD', 'OUTPUT');
CREATE TYPE applicability_mode AS ENUM ('ALWAYS', 'CASE_DEPENDENT');
CREATE TYPE applicability_decision AS ENUM ('APPLIES', 'NOT_APPLICABLE', 'UNDETERMINED');
CREATE TYPE cardinality AS ENUM ('ONE', 'MULTIPLE');
CREATE TYPE verification_scope AS ENUM ('PROCESS', 'MACRO_ACTIVITY');
CREATE TYPE run_status AS ENUM ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED');
CREATE TYPE result_status AS ENUM ('COMPLIES', 'DOES_NOT_COMPLY', 'REVIEW_REQUIRED', 'NOT_APPLICABLE');
CREATE TYPE decision_origin AS ENUM ('DETERMINISTIC', 'AI_APPLICABILITY', 'HUMAN', 'NOT_EVALUATED');
CREATE TYPE human_decision_type AS ENUM ('APPLICABILITY', 'RESULT_STATUS');
CREATE TYPE source_type AS ENUM ('DRIVE', 'REDMINE', 'IMPUTATIONS', 'TOOL_RECORD');

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_users_email_lower ON users (lower(email));

CREATE TABLE registry_definitions (
    id UUID PRIMARY KEY,
    key VARCHAR(100) NOT NULL UNIQUE,
    canonical_name VARCHAR(255) NOT NULL,
    evidence_type evidence_type NOT NULL,
    official_nomenclature TEXT,
    preloaded BOOLEAN NOT NULL
);

CREATE TABLE processes (
    id UUID PRIMARY KEY,
    code process_code NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    m2_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE process_versions (
    id UUID PRIMARY KEY,
    process_id UUID NOT NULL,
    official_version VARCHAR(50) NOT NULL,
    version_date DATE NOT NULL,
    status process_status NOT NULL,
    revision INTEGER NOT NULL,
    objective TEXT NOT NULL,
    owner VARCHAR(255) NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_process_version_official UNIQUE (process_id, official_version),
    CONSTRAINT uq_process_version_id_process UNIQUE (id, process_id),
    CONSTRAINT fk_process_version_process FOREIGN KEY (process_id) REFERENCES processes(id)
);

CREATE TABLE process_policies (
    id UUID PRIMARY KEY,
    process_version_id UUID NOT NULL,
    sort_order INTEGER NOT NULL,
    text TEXT NOT NULL,
    CONSTRAINT fk_process_policy_version FOREIGN KEY (process_version_id) REFERENCES process_versions(id)
);

CREATE TABLE macro_activities (
    id UUID PRIMARY KEY,
    process_version_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL,
    CONSTRAINT uq_macro_activity_id_version UNIQUE (id, process_version_id),
    CONSTRAINT fk_macro_activity_version FOREIGN KEY (process_version_id) REFERENCES process_versions(id)
);

CREATE TABLE activities (
    id UUID PRIMARY KEY,
    macro_activity_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    responsible VARCHAR(255),
    instructions TEXT NOT NULL,
    eta_text VARCHAR(255),
    sort_order INTEGER NOT NULL,
    CONSTRAINT uq_activity_id_macro UNIQUE (id, macro_activity_id),
    CONSTRAINT fk_activity_macro FOREIGN KEY (macro_activity_id) REFERENCES macro_activities(id)
);

CREATE TABLE evidence_expectations (
    id UUID PRIMARY KEY,
    macro_activity_id UUID NOT NULL,
    activity_id UUID,
    registry_definition_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    applicability applicability_mode NOT NULL,
    cardinality cardinality NOT NULL,
    location evidence_location NOT NULL,
    sort_order INTEGER NOT NULL,
    CONSTRAINT fk_expectation_macro FOREIGN KEY (macro_activity_id) REFERENCES macro_activities(id),
    CONSTRAINT fk_expectation_registry FOREIGN KEY (registry_definition_id) REFERENCES registry_definitions(id),
    CONSTRAINT fk_expectation_activity_macro FOREIGN KEY (activity_id, macro_activity_id) REFERENCES activities(id, macro_activity_id),
    CONSTRAINT chk_expectation_location_activity CHECK (
        (location IN ('INPUT', 'OUTPUT') AND activity_id IS NULL)
        OR
        (location = 'ACTIVITY_RECORD' AND activity_id IS NOT NULL)
    )
);

CREATE TABLE services (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    client VARCHAR(255) NOT NULL,
    service_type service_type NOT NULL,
    status service_status NOT NULL,
    revision INTEGER NOT NULL,
    drive_folder_id VARCHAR(255),
    drive_folder_name VARCHAR(255),
    redmine_project_id VARCHAR(255),
    redmine_project_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE service_process_versions (
    service_id UUID NOT NULL,
    process_id UUID NOT NULL,
    process_version_id UUID NOT NULL,
    PRIMARY KEY (service_id, process_version_id),
    CONSTRAINT uq_service_process UNIQUE (service_id, process_id),
    CONSTRAINT fk_service_process_service FOREIGN KEY (service_id) REFERENCES services(id),
    CONSTRAINT fk_service_process_version FOREIGN KEY (process_version_id, process_id)
        REFERENCES process_versions(id, process_id)
);

CREATE TABLE verification_runs (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL,
    service_type_snapshot service_type NOT NULL,
    service_revision_snapshot INTEGER NOT NULL,
    process_version_id UUID NOT NULL,
    scope verification_scope NOT NULL,
    macro_activity_id UUID,
    note TEXT,
    status run_status NOT NULL,
    started_by UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    failure_message TEXT,
    drive_folder_id VARCHAR(255),
    drive_folder_name VARCHAR(255),
    redmine_project_id VARCHAR(255),
    redmine_project_name VARCHAR(255),
    CONSTRAINT fk_run_service FOREIGN KEY (service_id) REFERENCES services(id),
    CONSTRAINT fk_run_process_version FOREIGN KEY (process_version_id) REFERENCES process_versions(id),
    CONSTRAINT fk_run_macro_version FOREIGN KEY (macro_activity_id, process_version_id)
        REFERENCES macro_activities(id, process_version_id),
    CONSTRAINT fk_run_started_by FOREIGN KEY (started_by) REFERENCES users(id),
    CONSTRAINT chk_run_scope_macro CHECK (
        (scope = 'PROCESS' AND macro_activity_id IS NULL)
        OR
        (scope = 'MACRO_ACTIVITY' AND macro_activity_id IS NOT NULL)
    )
);

CREATE TABLE verification_ticket_selections (
    run_id UUID NOT NULL,
    group_id VARCHAR(255) NOT NULL,
    ticket_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (run_id, group_id, ticket_id),
    CONSTRAINT fk_ticket_selection_run FOREIGN KEY (run_id) REFERENCES verification_runs(id)
);

CREATE TABLE verification_results (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    expectation_id UUID NOT NULL,
    macro_activity_id UUID NOT NULL,
    activity_id UUID,
    display_name VARCHAR(255) NOT NULL,
    evidence_type evidence_type NOT NULL,
    location evidence_location NOT NULL,
    subject_ticket_id VARCHAR(255),
    applicability_decision applicability_decision NOT NULL,
    applicability_origin decision_origin NOT NULL,
    applicability_rationale TEXT,
    proposed_status result_status NOT NULL,
    effective_status result_status NOT NULL,
    rationale TEXT,
    revision INTEGER NOT NULL,
    CONSTRAINT uq_verification_result UNIQUE NULLS NOT DISTINCT (run_id, expectation_id, subject_ticket_id),
    CONSTRAINT fk_result_run FOREIGN KEY (run_id) REFERENCES verification_runs(id),
    CONSTRAINT fk_result_expectation FOREIGN KEY (expectation_id) REFERENCES evidence_expectations(id),
    CONSTRAINT fk_result_macro FOREIGN KEY (macro_activity_id) REFERENCES macro_activities(id),
    CONSTRAINT fk_result_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
    CONSTRAINT chk_result_ticket_id_nonempty CHECK (
        subject_ticket_id IS NULL OR length(trim(subject_ticket_id)) > 0
    )
);

CREATE TABLE verification_result_limitations (
    id UUID PRIMARY KEY,
    result_id UUID NOT NULL,
    sort_order INTEGER NOT NULL,
    limitation TEXT NOT NULL,
    CONSTRAINT fk_result_limitation_result FOREIGN KEY (result_id) REFERENCES verification_results(id)
);

CREATE TABLE verification_result_evidence (
    id UUID PRIMARY KEY,
    result_id UUID NOT NULL,
    kind evidence_type NOT NULL,
    source source_type NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    web_url TEXT,
    observed_at TIMESTAMPTZ NOT NULL,
    ticket_id VARCHAR(255),
    CONSTRAINT fk_result_evidence_result FOREIGN KEY (result_id) REFERENCES verification_results(id)
);

CREATE TABLE human_decisions (
    id UUID PRIMARY KEY,
    result_id UUID NOT NULL,
    decision_type human_decision_type NOT NULL,
    applicability_value applicability_decision,
    status_value result_status,
    rationale TEXT NOT NULL,
    decided_by UUID NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_human_decision_result FOREIGN KEY (result_id) REFERENCES verification_results(id),
    CONSTRAINT fk_human_decision_user FOREIGN KEY (decided_by) REFERENCES users(id),
    CONSTRAINT chk_human_decision_value CHECK (
        (decision_type = 'APPLICABILITY'
            AND applicability_value IS NOT NULL
            AND applicability_value IN ('APPLIES', 'NOT_APPLICABLE')
            AND status_value IS NULL)
        OR
        (decision_type = 'RESULT_STATUS'
            AND applicability_value IS NULL
            AND status_value IS NOT NULL)
    )
);

CREATE INDEX idx_human_decisions_result_time ON human_decisions (result_id, decided_at);

CREATE OR REPLACE FUNCTION validate_service_process_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    version_status process_status;
    process_kind process_code;
BEGIN
    SELECT pv.status, p.code
      INTO version_status, process_kind
      FROM process_versions pv
      JOIN processes p ON p.id = pv.process_id
     WHERE pv.id = NEW.process_version_id
       AND pv.process_id = NEW.process_id;

    IF version_status IS DISTINCT FROM 'PUBLISHED' THEN
        RAISE EXCEPTION 'El servicio solo puede asignar versiones PUBLISHED';
    END IF;

    IF process_kind = 'QUALITY_CONTINUOUS_IMPROVEMENT' THEN
        RAISE EXCEPTION 'QUALITY_CONTINUOUS_IMPROVEMENT no puede asignarse a un servicio en esta entrega';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_service_process_version
BEFORE INSERT OR UPDATE ON service_process_versions
FOR EACH ROW
EXECUTE FUNCTION validate_service_process_version();
