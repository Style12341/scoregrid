package com.scoregrid.tournament.match.infrastructure.persistence;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.team.infrastructure.persistence.TeamJpaEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class MatchRepositoryAdapter implements MatchRepository {

    private final MatchJpaRepository jpaRepository;
    private final EntityManager em;

    MatchRepositoryAdapter(MatchJpaRepository jpaRepository, EntityManager em) {
        this.jpaRepository = jpaRepository;
        this.em = em;
    }

    @Override
    public Match save(Match match) {
        TeamJpaEntity homeEntity = em.getReference(TeamJpaEntity.class, match.getHomeTeam().id());
        TeamJpaEntity awayEntity = em.getReference(TeamJpaEntity.class, match.getAwayTeam().id());
        var entity = MatchMapper.toEntity(match, homeEntity, awayEntity);
        var saved = jpaRepository.save(entity);
        return MatchMapper.toDomain(saved);
    }

    @Override
    public Optional<Match> findById(Long id) {
        return jpaRepository.findById(id).map(MatchMapper::toDomain);
    }

    @Override
    public List<Match> findByTournamentId(Long tournamentId) {
        return jpaRepository.findByTournamentIdOrderByStartTimeAsc(tournamentId)
                .stream()
                .map(MatchMapper::toDomain)
                .toList();
    }

    @Override
    public List<Match> findByTournamentIdAndStatus(Long tournamentId, MatchStatus status) {
        return jpaRepository.findByTournamentIdAndStatusOrderByStartTimeAsc(
                        tournamentId, status.name())
                .stream()
                .map(MatchMapper::toDomain)
                .toList();
    }
}
