-- Apply after creating the tables/enums/foreign keys from the companion DBML.
-- DBML expresses most constraints, but a normal UNIQUE index permits repeated
-- NULL ticket IDs in PostgreSQL, and CHECK cannot inspect another table.

-- An expectation evaluated without a specific ticket has a NULL
-- subject_ticket_id. Allow only one result for that run and expectation.
-- The DBML composite UNIQUE index covers results with a non-NULL ticket ID.
CREATE UNIQUE INDEX IF NOT EXISTS uq_verification_results_without_ticket
  ON verification_results (run_id, expectation_id)
  WHERE subject_ticket_id IS NULL;

-- At assignment time, the version must be published and Quality must not be
-- attached to a service. The DBML composite FK separately proves that the
-- selected version belongs to the selected process.
CREATE OR REPLACE FUNCTION validate_service_process_assignment()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  selected_process record;
BEGIN
  SELECT pv.status, p.code
    INTO selected_process
    FROM process_versions AS pv
    JOIN processes AS p ON p.id = pv.process_id
   WHERE pv.id = NEW.process_version_id
     AND pv.process_id = NEW.process_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Process version % does not belong to process %',
      NEW.process_version_id, NEW.process_id;
  END IF;

  IF selected_process.status <> 'PUBLISHED' THEN
    RAISE EXCEPTION 'Only published process versions may be assigned to services';
  END IF;

  IF selected_process.code = 'QUALITY_CONTINUOUS_IMPROVEMENT' THEN
    RAISE EXCEPTION 'Quality and Continuous Improvement is not assigned to services in M1/M2';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_service_process_assignment
  ON service_process_versions;
CREATE TRIGGER trg_validate_service_process_assignment
BEFORE INSERT OR UPDATE OF service_id, process_id, process_version_id
  ON service_process_versions
FOR EACH ROW
EXECUTE FUNCTION validate_service_process_assignment();

-- Capture the service values at the instant a run is created. Later edits to
-- the service do not rewrite the historical run. The selected process version
-- must be assigned to the service at that instant.
CREATE OR REPLACE FUNCTION snapshot_verification_service()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  service_row services%ROWTYPE;
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM service_process_versions AS spv
     WHERE spv.service_id = NEW.service_id
       AND spv.process_version_id = NEW.process_version_id
  ) THEN
    RAISE EXCEPTION 'Process version % is not assigned to service %',
      NEW.process_version_id, NEW.service_id;
  END IF;

  SELECT s.*
    INTO service_row
    FROM services AS s
   WHERE s.id = NEW.service_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Service % does not exist', NEW.service_id;
  END IF;

  -- The caller passes StartVerificationRequest.serviceRevision. Reject a
  -- concurrent service edit instead of silently running against new settings.
  IF NEW.service_revision_snapshot IS DISTINCT FROM service_row.revision THEN
    RAISE EXCEPTION 'Service revision changed: requested %, current %',
      NEW.service_revision_snapshot, service_row.revision;
  END IF;

  NEW.service_type_snapshot := service_row.service_type;
  NEW.service_revision_snapshot := service_row.revision;
  NEW.drive_folder_id := service_row.drive_folder_id;
  NEW.drive_folder_name := service_row.drive_folder_name;
  NEW.redmine_project_id := service_row.redmine_project_id;
  NEW.redmine_project_name := service_row.redmine_project_name;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_snapshot_verification_service
  ON verification_runs;
CREATE TRIGGER trg_snapshot_verification_service
BEFORE INSERT ON verification_runs
FOR EACH ROW
EXECUTE FUNCTION snapshot_verification_service();

-- A run may advance its status and completion data, but its identity and the
-- service/source values used at start are historical facts.
CREATE OR REPLACE FUNCTION protect_verification_run_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF ROW(OLD.service_id, OLD.process_version_id, OLD.scope,
         OLD.macro_activity_id, OLD.service_type_snapshot,
         OLD.service_revision_snapshot, OLD.drive_folder_id,
         OLD.drive_folder_name, OLD.redmine_project_id,
         OLD.redmine_project_name, OLD.started_by, OLD.started_at)
     IS DISTINCT FROM
     ROW(NEW.service_id, NEW.process_version_id, NEW.scope,
         NEW.macro_activity_id, NEW.service_type_snapshot,
         NEW.service_revision_snapshot, NEW.drive_folder_id,
         NEW.drive_folder_name, NEW.redmine_project_id,
         NEW.redmine_project_name, NEW.started_by, NEW.started_at) THEN
    RAISE EXCEPTION 'A verification run snapshot cannot be changed';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_verification_run_snapshot
  ON verification_runs;
CREATE TRIGGER trg_protect_verification_run_snapshot
BEFORE UPDATE ON verification_runs
FOR EACH ROW
EXECUTE FUNCTION protect_verification_run_snapshot();

-- The catalog is create-only in the current API. An existing catalog record
-- must not change under a published process version. Decision events are also
-- append-only, so corrections are inserted as new events.
CREATE OR REPLACE FUNCTION reject_immutable_row_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION '% is append-only; insert a new row instead', TG_TABLE_NAME;
END;
$$;

DROP TRIGGER IF EXISTS trg_registry_definitions_immutable
  ON registry_definitions;
CREATE TRIGGER trg_registry_definitions_immutable
BEFORE UPDATE OR DELETE ON registry_definitions
FOR EACH ROW
EXECUTE FUNCTION reject_immutable_row_mutation();

DROP TRIGGER IF EXISTS trg_human_decisions_immutable
  ON human_decisions;
CREATE TRIGGER trg_human_decisions_immutable
BEFORE UPDATE OR DELETE ON human_decisions
FOR EACH ROW
EXECUTE FUNCTION reject_immutable_row_mutation();

-- Ticket IDs selected for a run are part of its historical input. A later
-- correction starts a new run; it does not rewrite the old selection.
DROP TRIGGER IF EXISTS trg_verification_ticket_selections_immutable
  ON verification_ticket_selections;
CREATE TRIGGER trg_verification_ticket_selections_immutable
BEFORE UPDATE OR DELETE ON verification_ticket_selections
FOR EACH ROW
EXECUTE FUNCTION reject_immutable_row_mutation();