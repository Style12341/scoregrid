package com.scoregrid.tournament.team.application;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.in.AssignTeamsToTournamentUseCase;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.team.domain.port.out.TournamentTeamRepository;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AssignTeamsToTournamentService implements AssignTeamsToTournamentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignTeamsToTournamentService.class);

    private final TournamentTeamRepository tournamentTeamRepository;
    private final TeamRepository teamRepository;
    private final TournamentRepository tournamentRepository;

    public AssignTeamsToTournamentService(TournamentTeamRepository tournamentTeamRepository,
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
        List<Long> addedTeamIds = new ArrayList<>();
        for (String teamIdStr : command.teamIds()) {
            Long teamId;
            try {
                teamId = Long.parseLong(teamIdStr);
            } catch (NumberFormatException e) {
                throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                        "Invalid team ID: " + teamIdStr);
            }
            if (!teamRepository.existsById(teamId)) {
                throw new DomainException(ErrorKind.VALIDATION, "VALIDATION_FAILED",
                        "Team " + teamIdStr + " not found");
            }
            // Idempotent: a team already in the tournament is skipped.
            if (!tournamentTeamRepository.existsByTournamentIdAndTeamId(command.tournamentId(), teamId)) {
                tournamentTeamRepository.assign(command.tournamentId(), teamId);
                addedTeamIds.add(teamId);
            }
        }
        if (!addedTeamIds.isEmpty()) {
            log.info("Teams assigned to tournament: tournamentId={} teamIds={}",
                    command.tournamentId(), addedTeamIds);
        }
        return tournamentTeamRepository.findByTournamentId(command.tournamentId());
    }
}
