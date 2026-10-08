package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.tournament.domain.model.PendingMatchesException;
import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.port.in.TransitionTournamentStatusUseCase;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransitionTournamentStatusService implements TransitionTournamentStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransitionTournamentStatusService.class);

    private final TournamentRepository tournamentRepository;
    private final MatchRepository matchRepository;
    private final MatchEventPublisher matchEventPublisher;

    public TransitionTournamentStatusService(TournamentRepository tournamentRepository,
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
        var previousStatus = tournament.getStatus();
        var matches = matchRepository.findByTournamentId(command.tournamentId());
        long pendingMatches = matches.stream().filter(Match::isPending).count();
        try {
            tournament.transitionTo(command.status(), pendingMatches);
        } catch (PendingMatchesException e) {
            throw new DomainException(ErrorKind.CONFLICT, "INVALID_MATCH_STATE", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED", e.getMessage());
        } catch (IllegalStateException e) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE", e.getMessage());
        }
        var saved = tournamentRepository.save(tournament);
        log.info("Tournament status changed: tournamentId={} fromStatus={} toStatus={}",
                saved.getId(), previousStatus, saved.getStatus());
        matches.forEach(match -> matchEventPublisher.updated(match, saved.getStatus()));
        return saved;
    }
}
