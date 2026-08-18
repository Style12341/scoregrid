ALTER TABLE matches
    DROP CONSTRAINT IF EXISTS matches_tournament_id_fkey;

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_tournament_id
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE;

ALTER TABLE groups
    DROP CONSTRAINT IF EXISTS groups_tournament_id_fkey;

ALTER TABLE groups
    ADD CONSTRAINT fk_groups_tournament_id
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE;

ALTER TABLE phases
    DROP CONSTRAINT IF EXISTS phases_tournament_id_fkey;

ALTER TABLE phases
    ADD CONSTRAINT fk_phases_tournament_id
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE;

ALTER TABLE matches
    DROP CONSTRAINT IF EXISTS fk_matches_group_id;

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_group_id
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE;

ALTER TABLE matches
    DROP CONSTRAINT IF EXISTS fk_matches_phase_id;

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_phase_id
    FOREIGN KEY (phase_id) REFERENCES phases(id) ON DELETE CASCADE;
