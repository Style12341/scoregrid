package com.scoregrid.tournament.phase.application;

import com.scoregrid.tournament.phase.domain.model.Phase;
import com.scoregrid.tournament.phase.domain.port.in.ListPhases;
import com.scoregrid.tournament.phase.domain.port.out.PhaseRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ListPhasesUseCase implements ListPhases {

    private final TournamentRepository tournamentRepository;
    private final PhaseRepository phaseRepository;

    public ListPhasesUseCase(TournamentRepository tournamentRepository,
                              PhaseRepository phaseRepository) {
        this.tournamentRepository = tournamentRepository;
        this.phaseRepository = phaseRepository;
    }

    @Override
    public List<Phase> execute(Long tournamentId) {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                    "Tournament not found: " + tournamentId);
        }
        return phaseRepository.findByTournamentId(tournamentId);
    }
}
