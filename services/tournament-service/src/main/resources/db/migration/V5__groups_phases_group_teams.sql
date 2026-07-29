CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id),
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
);

CREATE TABLE phases (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id),
    name VARCHAR(100),
    type VARCHAR(20) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
);

CREATE TABLE group_teams (
    group_id BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL REFERENCES teams(id),
    PRIMARY KEY (group_id, team_id)
);

-- Note: "team cannot belong to more than one group per tournament" invariant
-- is enforced in the application layer (AssignTeamsToGroupUseCase). A database-
-- level unique constraint across the groups/group_teams join would require a
-- redundant tournament_id column on group_teams, which is deferred per the
-- design decision in design.md §10 Decision 4.
