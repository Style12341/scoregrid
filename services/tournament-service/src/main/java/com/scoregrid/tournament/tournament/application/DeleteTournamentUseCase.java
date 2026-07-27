package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
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
        if (!tournament.canBeDeleted()) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Only DRAFT tournaments can be deleted. Status: " + tournament.getStatus());
        }
        tournamentRepository.delete(id);
    }
}
