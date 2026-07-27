-- Forward-only migration: change the tournaments.status column default from
-- 'ACTIVE' (set by V1) to 'DRAFT' so new tournament rows created without an
-- explicit status value start in the correct state.
--
-- This ALTER only changes the default value for future INSERTs. It does NOT
-- modify the status of any existing row.

ALTER TABLE tournaments ALTER COLUMN status SET DEFAULT 'DRAFT';
