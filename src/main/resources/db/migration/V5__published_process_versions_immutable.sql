-- TC-149: published process versions are immutable and publication is one-way.

CREATE OR REPLACE FUNCTION protect_process_version_root()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.status = 'PUBLISHED'::process_status THEN
      RAISE EXCEPTION 'Published process version % is immutable', OLD.id;
    END IF;
    RETURN OLD;
  END IF;

  IF OLD.status = 'PUBLISHED'::process_status THEN
    RAISE EXCEPTION 'Published process version % is immutable', OLD.id;
  END IF;

  IF NEW.status IS DISTINCT FROM OLD.status
     AND NOT (OLD.status = 'DRAFT'::process_status AND NEW.status = 'PUBLISHED'::process_status) THEN
    RAISE EXCEPTION 'Unsupported process version status transition: % -> %', OLD.status, NEW.status;
  END IF;

  IF OLD.status = 'DRAFT'::process_status
     AND NEW.status = 'PUBLISHED'::process_status
     AND ROW(OLD.process_id, OLD.official_version, OLD.version_date, OLD.objective, OLD.owner, OLD.created_at)
         IS DISTINCT FROM
         ROW(NEW.process_id, NEW.official_version, NEW.version_date, NEW.objective, NEW.owner, NEW.created_at) THEN
    RAISE EXCEPTION 'Publication may only change status, revision and published_at';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_process_versions_immutable ON process_versions;
CREATE TRIGGER trg_process_versions_immutable
BEFORE UPDATE OR DELETE ON process_versions
FOR EACH ROW
EXECUTE FUNCTION protect_process_version_root();

CREATE OR REPLACE FUNCTION assert_process_version_draft(target_version_id uuid)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  current_status process_status;
BEGIN
  SELECT status INTO current_status
  FROM process_versions
  WHERE id = target_version_id;

  IF current_status IS NULL THEN
    RAISE EXCEPTION 'Process version % does not exist', target_version_id;
  END IF;

  IF current_status <> 'DRAFT'::process_status THEN
    RAISE EXCEPTION 'Published process version % is immutable', target_version_id;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION protect_process_policy_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM assert_process_version_draft(CASE WHEN TG_OP = 'DELETE' THEN OLD.process_version_id ELSE NEW.process_version_id END);
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE OR REPLACE FUNCTION protect_macro_activity_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM assert_process_version_draft(CASE WHEN TG_OP = 'DELETE' THEN OLD.process_version_id ELSE NEW.process_version_id END);
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE OR REPLACE FUNCTION protect_activity_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  target_macro_id uuid;
  target_version_id uuid;
BEGIN
  target_macro_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.macro_activity_id ELSE NEW.macro_activity_id END;

  SELECT process_version_id INTO target_version_id
  FROM macro_activities
  WHERE id = target_macro_id;

  -- A cascading delete of a DRAFT macroactivity may reach this trigger after
  -- the parent row is no longer visible. The macroactivity trigger already
  -- protected the parent version in that path.
  IF target_version_id IS NOT NULL THEN
    PERFORM assert_process_version_draft(target_version_id);
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE OR REPLACE FUNCTION protect_evidence_expectation_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  target_macro_id uuid;
  target_version_id uuid;
BEGIN
  target_macro_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.macro_activity_id ELSE NEW.macro_activity_id END;

  SELECT process_version_id INTO target_version_id
  FROM macro_activities
  WHERE id = target_macro_id;

  IF target_version_id IS NOT NULL THEN
    PERFORM assert_process_version_draft(target_version_id);
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

DROP TRIGGER IF EXISTS trg_process_policies_version_immutable ON process_policies;
CREATE TRIGGER trg_process_policies_version_immutable
BEFORE INSERT OR UPDATE OR DELETE ON process_policies
FOR EACH ROW
EXECUTE FUNCTION protect_process_policy_mutation();

DROP TRIGGER IF EXISTS trg_macro_activities_version_immutable ON macro_activities;
CREATE TRIGGER trg_macro_activities_version_immutable
BEFORE INSERT OR UPDATE OR DELETE ON macro_activities
FOR EACH ROW
EXECUTE FUNCTION protect_macro_activity_mutation();

DROP TRIGGER IF EXISTS trg_activities_version_immutable ON activities;
CREATE TRIGGER trg_activities_version_immutable
BEFORE INSERT OR UPDATE OR DELETE ON activities
FOR EACH ROW
EXECUTE FUNCTION protect_activity_mutation();

DROP TRIGGER IF EXISTS trg_evidence_expectations_version_immutable ON evidence_expectations;
CREATE TRIGGER trg_evidence_expectations_version_immutable
BEFORE INSERT OR UPDATE OR DELETE ON evidence_expectations
FOR EACH ROW
EXECUTE FUNCTION protect_evidence_expectation_mutation();
