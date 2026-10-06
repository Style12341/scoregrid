package com.scoregrid.tournament.team.application;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.in.CreateTeamUseCase;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateTeamService implements CreateTeamUseCase {

    private final TeamRepository teamRepository;

    public CreateTeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public Team execute(Command command) {
        var team = Team.create(command.name(), command.shortName(),
                command.country(), command.logoUrl());
        return teamRepository.save(team);
    }
}
