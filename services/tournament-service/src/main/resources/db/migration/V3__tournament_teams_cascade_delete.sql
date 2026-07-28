-- Forward-only migration: add ON DELETE CASCADE to the tournament_teams FK
-- so deleting a DRAFT tournament automatically removes its team assignments.
--
-- Deletion is only allowed in DRAFT status where no participants exist.
-- There is no cascade to participants because none should exist at that point.

ALTER TABLE tournament_teams
    DROP CONSTRAINT tournament_teams_tournament_id_fkey,
    ADD CONSTRAINT tournament_teams_tournament_id_fkey
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id)
        ON DELETE CASCADE;
