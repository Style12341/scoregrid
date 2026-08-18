package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.port.in.ListMatches;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class ListMatchesUseCase implements ListMatches {

    private final TournamentRepository tournamentRepository;
    private final MatchRepository matchRepository;

    public ListMatchesUseCase(TournamentRepository tournamentRepository,
                               MatchRepository matchRepository) {
        this.tournamentRepository = tournamentRepository;
        this.matchRepository = matchRepository;
    }

    @Override
    public List<Match> execute(Long tournamentId, Optional<MatchStatus> statusFilter) {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                    "Tournament not found: " + tournamentId);
        }
        if (statusFilter.isEmpty()) {
            return matchRepository.findByTournamentId(tournamentId);
        }

        MatchStatus status = statusFilter.get();
        if (status != MatchStatus.SCHEDULED && status != MatchStatus.IN_PROGRESS) {
            return matchRepository.findByTournamentIdAndStatus(tournamentId, status);
        }

        Instant now = Instant.now();
        return matchRepository.findByTournamentId(tournamentId).stream()
                .filter(match -> {
                    if (status == MatchStatus.IN_PROGRESS) {
                        return match.getStatus() == MatchStatus.IN_PROGRESS
                                || match.isInProgress(now);
                    }
                    return match.getStatus() == MatchStatus.SCHEDULED
                            && !match.isInProgress(now);
                })
                .toList();
    }
}
