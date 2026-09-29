-- Identity invariants that must hold even when two requests arrive concurrently.
-- The application canonicalizes emails to lowercase before insert, while this
-- functional index makes that invariant resilient to direct/legacy writes too.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_normalized
    ON users (LOWER(email));
