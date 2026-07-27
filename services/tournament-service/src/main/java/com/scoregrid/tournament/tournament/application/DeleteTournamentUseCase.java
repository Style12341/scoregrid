package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.DeleteTournament;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeleteTournamentUseCase implements DeleteTournament {

    private final TournamentRepository tournamentRepository;

    public DeleteTournamentUseCase(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public void execute(Long id) {
        var tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + id));

        if (tournament.getStatus() == TournamentStatus.DRAFT) {
            tournamentRepository.delete(id);
            return;
        }

        if (tournament.getStatus() == TournamentStatus.ACTIVE) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Cannot delete an ACTIVE tournament. Transition it to FINISHED or CANCELLED first.");
        }

        // FINISHED or CANCELLED — terminal states cannot be deleted
        throw new DomainException(ErrorKind.CONFLICT, "CONFLICT",
                "Cannot delete a " + tournament.getStatus() + " tournament. Only DRAFT tournaments can be deleted.");
    }
}
