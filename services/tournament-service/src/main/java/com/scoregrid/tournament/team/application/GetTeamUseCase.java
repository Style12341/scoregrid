package com.scoregrid.tournament.team.application;

import com.scoregrid.tournament.team.domain.model.Team;
import com.scoregrid.tournament.team.domain.port.in.GetTeam;
import com.scoregrid.tournament.team.domain.port.out.TeamRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetTeamUseCase implements GetTeam {

    private final TeamRepository teamRepository;

    public GetTeamUseCase(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public Team execute(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Team not found: " + id));
    }
}
