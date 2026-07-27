package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.ListTournaments;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ListTournamentsUseCase implements ListTournaments {

    private final TournamentRepository tournamentRepository;

    public ListTournamentsUseCase(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public Result execute(Optional<TournamentStatus> statusFilter, int page, int size) {
        int offset = page * size;
        var content = statusFilter
                .map(s -> tournamentRepository.findAllByStatus(s, offset, size))
                .orElseGet(() -> tournamentRepository.findAllPaginated(offset, size));
        long total = statusFilter
                .map(tournamentRepository::countByStatus)
                .orElseGet(tournamentRepository::count);
        int totalPages = (int) Math.ceil((double) total / size);
        return new Result(content, total, totalPages, page, size);
    }
}
