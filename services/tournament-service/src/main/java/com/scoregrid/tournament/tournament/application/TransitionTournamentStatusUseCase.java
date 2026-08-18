package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.port.in.TransitionTournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransitionTournamentStatusUseCase implements TransitionTournamentStatus {

    private final TournamentRepository tournamentRepository;
    private final MatchRepository matchRepository;
    private final MatchEventPublisher matchEventPublisher;

    public TransitionTournamentStatusUseCase(TournamentRepository tournamentRepository,
                                             MatchRepository matchRepository,
                                             MatchEventPublisher matchEventPublisher) {
        this.tournamentRepository = tournamentRepository;
        this.matchRepository = matchRepository;
        this.matchEventPublisher = matchEventPublisher;
    }

    @Override
    public Tournament execute(Command command) {
        var tournament = tournamentRepository.findById(command.tournamentId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + command.tournamentId()));
        try {
            tournament.transitionTo(command.status());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED", e.getMessage());
        } catch (IllegalStateException e) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE", e.getMessage());
        }
        var saved = tournamentRepository.save(tournament);
        matchRepository.findByTournamentId(saved.getId())
                .forEach(match -> matchEventPublisher.updated(match, saved.getStatus()));
        return saved;
    }
}
