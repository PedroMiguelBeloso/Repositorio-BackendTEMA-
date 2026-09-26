-- Tipos de datos (ENUMs)
CREATE TYPE role_enum AS ENUM ('ADMIN', 'READER');
CREATE TYPE evidence_type AS ENUM ('DOCUMENT', 'FOLDER', 'TICKET', 'IMPUTATIONS', 'TOOL_RECORD');
CREATE TYPE process_code AS ENUM ('START_UP', 'TAKE_OVER', 'SUPPORT_SOFTWARE_FACTORY', 'QUALITY_CONTINUOUS_IMPROVEMENT');
CREATE TYPE process_status AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');
CREATE TYPE evidence_location AS ENUM ('INPUT', 'ACTIVITY_RECORD', 'OUTPUT');
CREATE TYPE applicability_mode AS ENUM ('ALWAYS', 'CASE_DEPENDENT');
CREATE TYPE cardinality_enum AS ENUM ('ONE', 'MULTIPLE');
CREATE TYPE service_type AS ENUM ('SUPPORT_ARIBA', 'SUPPORT_ERP', 'SOFTWARE_FACTORY', 'PROJECT_SAP_ARIBA', 'PROJECT_ERP');
CREATE TYPE service_status AS ENUM ('ACTIVE', 'INACTIVE');
CREATE TYPE verification_scope AS ENUM ('PROCESS', 'MACRO_ACTIVITY');
CREATE TYPE run_status AS ENUM ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED');
CREATE TYPE result_status AS ENUM ('COMPLIES', 'DOES_NOT_COMPLY', 'REVIEW_REQUIRED', 'NOT_APPLICABLE');
CREATE TYPE applicability_decision AS ENUM ('APPLIES', 'NOT_APPLICABLE', 'UNDETERMINED');
CREATE TYPE human_decision_type AS ENUM ('APPLICABILITY', 'RESULT_STATUS');
CREATE TYPE source_type AS ENUM ('DRIVE', 'REDMINE', 'IMPUTATIONS', 'TOOL_RECORD');
CREATE TYPE decision_origin AS ENUM ('DETERMINISTIC', 'AI_APPLICABILITY', 'HUMAN', 'NOT_EVALUATED');

-- Tablas de Identidad y Catálogo
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role role_enum NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE registry_definitions (
    id UUID PRIMARY KEY,
    key VARCHAR(255) UNIQUE NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    evidence_type evidence_type NOT NULL,
    official_nomenclature VARCHAR(255),
    preloaded BOOLEAN NOT NULL DEFAULT false
);

-- Tablas de Definición de Procesos (M1)
CREATE TABLE processes (
    id UUID PRIMARY KEY,
    code process_code UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    m2_enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE process_versions (
    id UUID PRIMARY KEY,
    process_id UUID NOT NULL REFERENCES processes(id),
    official_version VARCHAR(255) NOT NULL,
    objective TEXT NOT NULL,
    owner VARCHAR(255) NOT NULL,
    version_date DATE NOT NULL,
    status process_status NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE process_policies (
    id UUID PRIMARY KEY,
    process_version_id UUID NOT NULL REFERENCES process_versions(id) ON DELETE CASCADE,
    "order" INT NOT NULL,
    text TEXT NOT NULL
);

CREATE TABLE macro_activities (
    id UUID PRIMARY KEY,
    process_version_id UUID NOT NULL REFERENCES process_versions(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    "order" INT NOT NULL
);

CREATE TABLE activities (
    id UUID PRIMARY KEY,
    macro_activity_id UUID NOT NULL REFERENCES macro_activities(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    responsible VARCHAR(255),
    instructions TEXT NOT NULL,
    eta_text VARCHAR(255),
    "order" INT NOT NULL
);

CREATE TABLE evidence_expectations (
    id UUID PRIMARY KEY,
    macro_activity_id UUID NOT NULL REFERENCES macro_activities(id) ON DELETE CASCADE,
    activity_id UUID REFERENCES activities(id) ON DELETE CASCADE,
    registry_definition_id UUID NOT NULL REFERENCES registry_definitions(id),
    display_name VARCHAR(255) NOT NULL,
    applicability applicability_mode NOT NULL,
    cardinality cardinality_enum NOT NULL,
    location evidence_location NOT NULL,
    "order" INT NOT NULL
);

-- Tablas de Configuración de Servicios (M1)
CREATE TABLE services (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    client VARCHAR(255) NOT NULL,
    service_type service_type NOT NULL,
    status service_status NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    drive_folder_id VARCHAR(255),
    drive_folder_name VARCHAR(255),
    redmine_project_id VARCHAR(255),
    redmine_project_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE service_process_versions (
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    process_id UUID NOT NULL REFERENCES processes(id),
    process_version_id UUID NOT NULL REFERENCES process_versions(id),
    PRIMARY KEY (service_id, process_id)
);

-- Tablas de Ejecución y Resultados de Verificación (M2)
CREATE TABLE verification_runs (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES services(id),
    process_version_id UUID NOT NULL REFERENCES process_versions(id),
    started_by UUID NOT NULL REFERENCES users(id),
    service_type_snapshot service_type NOT NULL,
    service_revision_snapshot INT NOT NULL,
    drive_folder_id VARCHAR(255),
    drive_folder_name VARCHAR(255),
    redmine_project_id VARCHAR(255),
    redmine_project_name VARCHAR(255),
    scope verification_scope NOT NULL,
    macro_activity_id UUID REFERENCES macro_activities(id),
    note TEXT,
    status run_status NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    failure_message TEXT
);

CREATE TABLE verification_ticket_selections (
    run_id UUID NOT NULL REFERENCES verification_runs(id) ON DELETE CASCADE,
    group_id VARCHAR(255) NOT NULL,
    ticket_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (run_id, group_id, ticket_id)
);

CREATE TABLE verification_results (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES verification_runs(id) ON DELETE CASCADE,
    expectation_id UUID NOT NULL REFERENCES evidence_expectations(id),
    macro_activity_id UUID NOT NULL REFERENCES macro_activities(id),
    activity_id UUID REFERENCES activities(id),
    subject_ticket_id VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    proposed_status result_status NOT NULL,
    effective_status result_status NOT NULL,
    applicability_decision applicability_decision NOT NULL,
    applicability_origin decision_origin NOT NULL,
    applicability_rationale TEXT,
    rationale TEXT NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    UNIQUE (run_id, expectation_id, subject_ticket_id)
);

CREATE TABLE verification_result_limitations (
    result_id UUID NOT NULL REFERENCES verification_results(id) ON DELETE CASCADE,
    text TEXT NOT NULL
);

CREATE TABLE verification_result_evidence (
    result_id UUID NOT NULL REFERENCES verification_results(id) ON DELETE CASCADE,
    kind evidence_type NOT NULL,
    source source_type NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    web_url TEXT,
    ticket_id VARCHAR(255),
    observed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE human_decisions (
    id UUID PRIMARY KEY,
    result_id UUID NOT NULL REFERENCES verification_results(id) ON DELETE CASCADE,
    decided_by UUID NOT NULL REFERENCES users(id),
    decision_type human_decision_type NOT NULL,
    applicability_value applicability_decision,
    status_value result_status,
    rationale TEXT NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);