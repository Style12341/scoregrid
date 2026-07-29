ALTER TABLE matches
    ADD CONSTRAINT fk_matches_group_id
    FOREIGN KEY (group_id) REFERENCES groups(id);

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_phase_id
    FOREIGN KEY (phase_id) REFERENCES phases(id);
