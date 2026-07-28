package com.scoregrid.tournament.team.application;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.in.ListTeams;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ListTeamsUseCase implements ListTeams {

    private final TeamRepository teamRepository;

    public ListTeamsUseCase(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public List<Team> execute() {
        return teamRepository.findAll();
    }
}
