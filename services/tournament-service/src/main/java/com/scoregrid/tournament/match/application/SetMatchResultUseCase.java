package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.match.domain.model.MatchStatus;
import com.scoregrid.tournament.match.domain.port.in.SetMatchResult;
import com.scoregrid.tournament.match.domain.port.out.MatchEventPublisher;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SetMatchResultUseCase implements SetMatchResult {

    private final MatchRepository matchRepository;
    private final MatchEventPublisher eventPublisher;

    public SetMatchResultUseCase(MatchRepository matchRepository,
                                  MatchEventPublisher eventPublisher) {
        this.matchRepository = matchRepository;
        this.eventPublisher = eventPublisher;
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

        try {
            match.loadResult(command.homeScore(), command.awayScore());
        } catch (IllegalStateException e) {
            String status = match.getStatus().name();
            if (status.equals("POSTPONED")) {
                throw new DomainException(ErrorKind.CONFLICT, "INVALID_MATCH_STATE",
                        "Cannot finish a postponed match");
            }
            if (status.equals("CANCELLED")) {
                throw new DomainException(ErrorKind.CONFLICT, "INVALID_MATCH_STATE",
                        "Cannot finish a cancelled match");
            }
            throw new DomainException(ErrorKind.UNPROCESSABLE, "INVALID_MATCH_STATE",
                    e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED", e.getMessage());
        }

        matchRepository.save(match);
        eventPublisher.finished(match);
    }
}
