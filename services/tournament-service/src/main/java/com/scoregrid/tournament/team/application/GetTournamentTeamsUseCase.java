package com.scoregrid.tournament.team.application;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.in.GetTournamentTeams;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetTournamentTeamsUseCase implements GetTournamentTeams {

    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentRepository tournamentRepository;

    public GetTournamentTeamsUseCase(TournamentTeamRepository tournamentTeamRepository,
                                      TournamentRepository tournamentRepository) {
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public List<Team> execute(Long tournamentId) {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                    "Tournament not found: " + tournamentId);
        }
        return tournamentTeamRepository.findByTournamentId(tournamentId);
    }
}
