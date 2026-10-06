package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.port.in.UpdateTournamentUseCase;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UpdateTournamentService implements UpdateTournamentUseCase {

    private final TournamentRepository tournamentRepository;

    public UpdateTournamentService(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public Tournament execute(Command command) {
        var tournament = tournamentRepository.findById(command.tournamentId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + command.tournamentId()));
        try {
            tournament.update(command.name(), command.description(),
                    command.startDate(), command.endDate());
        } catch (IllegalStateException e) {
            // FINISHED and CANCELLED are terminal. Same code as a refused
            // status transition (TransitionTournamentStatusService): a wrong
            // state is a 409, not the 500 an uncaught exception becomes.
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE", e.getMessage());
        }
        return tournamentRepository.save(tournament);
    }
}
