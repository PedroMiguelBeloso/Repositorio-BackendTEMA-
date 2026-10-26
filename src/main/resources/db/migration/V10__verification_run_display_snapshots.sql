-- Names shown in history must remain stable when a service is renamed or reassigned.
ALTER TABLE verification_runs
  ADD COLUMN service_name_snapshot VARCHAR(255),
  ADD COLUMN process_name_snapshot VARCHAR(255),
  ADD COLUMN process_official_version_snapshot VARCHAR(255),
  ADD COLUMN macro_activity_name_snapshot VARCHAR(255);

-- Existing runs cannot recover a former service name. This is the best available
-- value at migration time; new runs capture the name at creation.
UPDATE verification_runs AS vr
SET service_name_snapshot = s.name,
    process_name_snapshot = p.name,
    process_official_version_snapshot = pv.official_version,
    macro_activity_name_snapshot = NULL
FROM services AS s, process_versions AS pv, processes AS p
WHERE vr.service_id = s.id AND vr.process_version_id = pv.id AND pv.process_id = p.id;

UPDATE verification_runs AS vr
SET macro_activity_name_snapshot = ma.name
FROM macro_activities AS ma
WHERE vr.macro_activity_id = ma.id;

ALTER TABLE verification_runs
  ALTER COLUMN service_name_snapshot SET NOT NULL,
  ALTER COLUMN process_name_snapshot SET NOT NULL,
  ALTER COLUMN process_official_version_snapshot SET NOT NULL;

CREATE OR REPLACE FUNCTION snapshot_verification_display_names()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  SELECT s.name, p.name, pv.official_version, ma.name
    INTO NEW.service_name_snapshot, NEW.process_name_snapshot,
         NEW.process_official_version_snapshot, NEW.macro_activity_name_snapshot
  FROM services AS s
  JOIN process_versions AS pv ON pv.id = NEW.process_version_id
  JOIN processes AS p ON p.id = pv.process_id
  LEFT JOIN macro_activities AS ma ON ma.id = NEW.macro_activity_id
  WHERE s.id = NEW.service_id;
  IF NEW.service_name_snapshot IS NULL THEN
    RAISE EXCEPTION 'Cannot capture display names for verification run';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_snapshot_verification_display_names
BEFORE INSERT ON verification_runs FOR EACH ROW
EXECUTE FUNCTION snapshot_verification_display_names();

CREATE OR REPLACE FUNCTION protect_verification_run_snapshot()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF ROW(OLD.service_id, OLD.process_version_id, OLD.scope,
         OLD.macro_activity_id, OLD.service_type_snapshot,
         OLD.service_revision_snapshot, OLD.drive_folder_id,
         OLD.drive_folder_name, OLD.redmine_project_id,
         OLD.redmine_project_name, OLD.started_by, OLD.started_at,
         OLD.service_name_snapshot, OLD.process_name_snapshot,
         OLD.process_official_version_snapshot, OLD.macro_activity_name_snapshot)
     IS DISTINCT FROM
     ROW(NEW.service_id, NEW.process_version_id, NEW.scope,
         NEW.macro_activity_id, NEW.service_type_snapshot,
         NEW.service_revision_snapshot, NEW.drive_folder_id,
         NEW.drive_folder_name, NEW.redmine_project_id,
         NEW.redmine_project_name, NEW.started_by, NEW.started_at,
         NEW.service_name_snapshot, NEW.process_name_snapshot,
         NEW.process_official_version_snapshot, NEW.macro_activity_name_snapshot) THEN
    RAISE EXCEPTION 'A verification run snapshot cannot be changed';
  END IF;
  RETURN NEW;
END;
$$;
