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
        return statusFilter
                .map(status -> matchRepository.findByTournamentIdAndStatus(tournamentId, status))
                .orElseGet(() -> matchRepository.findByTournamentId(tournamentId));
    }
}
