package com.scoregrid.tournament.team.application;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.in.AssignTeamsToTournament;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AssignTeamsToTournamentUseCase implements AssignTeamsToTournament {

    private final TournamentTeamRepository tournamentTeamRepository;
    private final TeamRepository teamRepository;
    private final TournamentRepository tournamentRepository;

    public AssignTeamsToTournamentUseCase(TournamentTeamRepository tournamentTeamRepository,
                                           TeamRepository teamRepository,
                                           TournamentRepository tournamentRepository) {
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.teamRepository = teamRepository;
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public List<Team> execute(Command command) {
        if (!tournamentRepository.existsById(command.tournamentId())) {
            throw new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                    "Tournament not found: " + command.tournamentId());
        }
        for (Long teamId : command.teamIds()) {
            if (!teamRepository.existsById(teamId)) {
                throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                        "Team " + teamId + " not found");
            }
            tournamentTeamRepository.assign(command.tournamentId(), teamId);
        }
        return tournamentTeamRepository.findByTournamentId(command.tournamentId());
    }
}
