package com.scoregrid.tournament.match.infrastructure.persistence;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.model.TeamRef;
import com.scoregrid.tournament.team.infrastructure.persistence.TeamJpaEntity;

final class MatchMapper {

    private MatchMapper() {
    }

    static Match toDomain(MatchEntity entity) {
        TeamRef home = toTeamRef(entity.getHomeTeam());
        TeamRef away = toTeamRef(entity.getAwayTeam());

        return Match.reconstitute(
                entity.getId(),
                entity.getTournamentId(),
                entity.getGroupId(),
                entity.getPhaseId(),
                home,
                away,
                entity.getStartTime(),
                MatchStatus.valueOf(entity.getStatus()),
                entity.getHomeScore(),
                entity.getAwayScore());
    }

    static MatchEntity toEntity(Match match, TeamJpaEntity homeTeam, TeamJpaEntity awayTeam) {
        var entity = new MatchEntity();
        entity.setId(match.getId());
        entity.setTournamentId(match.getTournamentId());
        entity.setGroupId(match.getGroupId());
        entity.setPhaseId(match.getPhaseId());
        entity.setHomeTeam(homeTeam);
        entity.setAwayTeam(awayTeam);
        entity.setStartTime(match.getStartTime());
        entity.setStatus(match.getStatus().name());
        entity.setHomeScore(match.getHomeScore());
        entity.setAwayScore(match.getAwayScore());
        return entity;
    }

    private static TeamRef toTeamRef(TeamJpaEntity entity) {
        return TeamRef.of(entity.getId(), entity.getName(), entity.getShortName());
    }
}
