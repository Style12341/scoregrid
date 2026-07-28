-- Forward-only migration: add ON DELETE CASCADE to the tournament_participants FK
-- so deleting a DRAFT tournament automatically removes its participant records.
--
-- V3 added cascade for tournament_teams. This migration adds the same for
-- tournament_participants, matching the spec requirement that deleting a DRAFT
-- tournament removes all related rows from both tables.

ALTER TABLE tournament_participants
    DROP CONSTRAINT tournament_participants_tournament_id_fkey,
    ADD CONSTRAINT tournament_participants_tournament_id_fkey
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id)
        ON DELETE CASCADE;
