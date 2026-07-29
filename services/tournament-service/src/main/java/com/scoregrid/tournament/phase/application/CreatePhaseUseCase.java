package com.scoregrid.tournament.phase.application;

import com.scoregrid.tournament.phase.domain.model.Phase;
import com.scoregrid.tournament.phase.domain.port.in.CreatePhase;
import com.scoregrid.tournament.phase.domain.port.out.PhaseRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreatePhaseUseCase implements CreatePhase {

    private final TournamentRepository tournamentRepository;
    private final PhaseRepository phaseRepository;

    public CreatePhaseUseCase(TournamentRepository tournamentRepository,
                               PhaseRepository phaseRepository) {
        this.tournamentRepository = tournamentRepository;
        this.phaseRepository = phaseRepository;
    }

    @Override
    public Phase execute(Command command) {
        if (!tournamentRepository.existsById(command.tournamentId())) {
            throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                    "Tournament not found: " + command.tournamentId());
        }
        var phase = Phase.create(command.tournamentId(), command.type(),
                command.name(), command.displayOrder());
        return phaseRepository.save(phase);
    }
}
