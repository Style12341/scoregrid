CREATE TABLE tournaments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE,
    end_date DATE,
    created_by VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    short_name VARCHAR(10),
    country VARCHAR(5),
    logo_url TEXT
);

CREATE TABLE tournament_teams (
    tournament_id BIGINT REFERENCES tournaments(id),
    team_id BIGINT REFERENCES teams(id),
    PRIMARY KEY (tournament_id, team_id)
);

CREATE TABLE tournament_participants (
    tournament_id BIGINT REFERENCES tournaments(id),
    user_id VARCHAR(50) NOT NULL,
    joined_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (tournament_id, user_id)
);

CREATE TABLE matches (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id),
    group_id BIGINT,
    phase_id BIGINT,
    home_team_id BIGINT NOT NULL REFERENCES teams(id),
    away_team_id BIGINT NOT NULL REFERENCES teams(id),
    start_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    home_score INT,
    away_score INT
);
