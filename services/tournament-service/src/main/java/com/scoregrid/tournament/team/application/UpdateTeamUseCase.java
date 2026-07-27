package com.scoregrid.tournament.team.application;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.in.UpdateTeam;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UpdateTeamUseCase implements UpdateTeam {

    private final TeamRepository teamRepository;

    public UpdateTeamUseCase(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public Team execute(Command command) {
        var team = teamRepository.findById(command.id())
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Team not found: " + command.id()));
        team.update(command.name(), command.shortName(), command.country(), command.logoUrl());
        return teamRepository.save(team);
    }
}
