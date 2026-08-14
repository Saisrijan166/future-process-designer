-- =====================================================================
-- Accounts, and who owns which process.
--
-- The ownership model, stated once here because the rest of the code enforces it:
--
--   process.owner_id IS NULL  ->  a shared sample. Visible to every signed-in
--                                 user, analysable by any of them, and editable
--                                 or deletable by none of them.
--   process.owner_id = <user> ->  private to that user. Nobody else can read it,
--                                 analyse it, change it or delete it.
--
-- Everything downstream of a process (activities, problems, opportunities,
-- future activities, interventions, analysis runs) inherits its scope through
-- the process foreign key, so no other table needs an owner column.
--
-- Deliberately still shared by everyone: knowledge_snippet, role, system_tool.
-- Those are reference data — the same research library grounds every analysis,
-- and role/system names are a common vocabulary rather than user content.
-- =====================================================================

CREATE TABLE app_user (
    id             UUID PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    display_name   VARCHAR(120) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    last_login_at  TIMESTAMPTZ
);

-- Addresses are compared case-insensitively, so uniqueness has to be too:
-- "Alice@x.com" and "alice@x.com" are the same account.
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (lower(email));

-- ON DELETE CASCADE: removing an account removes the work that belonged to it.
-- The shared samples survive, because their owner_id is NULL rather than a user.
ALTER TABLE process
    ADD COLUMN owner_id UUID REFERENCES app_user (id) ON DELETE CASCADE;

-- The listing filters on this on every request.
CREATE INDEX idx_process_owner ON process (owner_id, created_at DESC);

COMMENT ON COLUMN process.owner_id IS
    'Owning account, or NULL for a shared sample that every user can see and analyse but none can modify.';
