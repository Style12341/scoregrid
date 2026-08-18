package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.match.domain.port.in.SetMatchResult;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SetMatchResultUseCase implements SetMatchResult {

    private final MatchRepository matchRepository;
    private final MatchEventPublisher eventPublisher;
    private final TournamentRepository tournamentRepository;

    public SetMatchResultUseCase(MatchRepository matchRepository,
                                  MatchEventPublisher eventPublisher,
                                  TournamentRepository tournamentRepository) {
        this.matchRepository = matchRepository;
        this.eventPublisher = eventPublisher;
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public void execute(Command command) {
        if (command.homeScore() < 0 || command.homeScore() > 99
                || command.awayScore() < 0 || command.awayScore() > 99) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                    "Scores must be between 0 and 99");
        }

        var match = matchRepository.findById(command.id())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Match not found: " + command.id()));
        var tournament = tournamentRepository.findById(match.getTournamentId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + match.getTournamentId()));

        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Results can only be loaded for an ACTIVE tournament");
        }

        try {
            match.loadResult(command.homeScore(), command.awayScore());
        } catch (IllegalStateException e) {
            if (match.getStatus().name().equals("POSTPONED")) {
                throw new DomainException(ErrorKind.CONFLICT, "INVALID_MATCH_STATE",
                        "Cannot finish a postponed match");
            }
            if (match.getStatus().name().equals("CANCELLED")) {
                throw new DomainException(ErrorKind.CONFLICT, "INVALID_MATCH_STATE",
                        "Cannot finish a cancelled match");
            }
            throw new DomainException(ErrorKind.CONFLICT, "INVALID_MATCH_STATE",
                    e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED", e.getMessage());
        }

        matchRepository.save(match);
        eventPublisher.finished(match);
        eventPublisher.updated(match, tournament.getStatus());
    }
}
