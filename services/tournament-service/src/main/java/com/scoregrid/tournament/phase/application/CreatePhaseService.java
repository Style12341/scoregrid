package com.scoregrid.tournament.phase.application;

import com.scoregrid.tournament.phase.domain.model.Phase;
import com.scoregrid.tournament.phase.domain.port.in.CreatePhaseUseCase;
import com.scoregrid.tournament.phase.domain.port.out.PhaseRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreatePhaseService implements CreatePhaseUseCase {

    private final TournamentRepository tournamentRepository;
    private final PhaseRepository phaseRepository;

    public CreatePhaseService(TournamentRepository tournamentRepository,
                               PhaseRepository phaseRepository) {
        this.tournamentRepository = tournamentRepository;
        this.phaseRepository = phaseRepository;
    }

    @Override
    public Phase execute(Command command) {
        var tournament = tournamentRepository.findById(command.tournamentId())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Tournament not found: " + command.tournamentId()));
        if (tournament.getStatus() != TournamentStatus.DRAFT
                && tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new DomainException(ErrorKind.CONFLICT, "TOURNAMENT_NOT_ACTIVE",
                    "Tournament is not configurable in state " + tournament.getStatus());
        }
        var phase = Phase.create(command.tournamentId(), command.type(),
                command.name(), command.displayOrder());
        return phaseRepository.save(phase);
    }
}
