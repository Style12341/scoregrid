package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.port.in.CreateTournament;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateTournamentUseCase implements CreateTournament {

    private final TournamentRepository tournamentRepository;

    public CreateTournamentUseCase(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public Tournament execute(Command command) {
        var tournament = Tournament.create(
                command.name(),
                command.description(),
                command.startDate(),
                command.endDate(),
                command.creatorId());
        return tournamentRepository.save(tournament);
    }
}
