package com.scoregrid.tournament.tournament.application;

import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;
import com.scoregrid.tournament.tournament.domain.port.in.ListTournamentsUseCase;
import com.scoregrid.tournament.tournament.domain.port.out.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ListTournamentsService implements ListTournamentsUseCase {

    private final TournamentRepository tournamentRepository;

    public ListTournamentsService(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public Result execute(Optional<TournamentStatus> statusFilter, int page, int size, boolean excludeDrafts) {
        int offset = page * size;
        if (excludeDrafts && statusFilter.filter(status -> status == TournamentStatus.DRAFT).isPresent()) {
            return new Result(List.of(), 0, 0, page, size);
        }
        var content = statusFilter
                .map(s -> tournamentRepository.findAllByStatus(s, offset, size))
                .orElseGet(() -> excludeDrafts
                        ? tournamentRepository.findAllExceptStatus(TournamentStatus.DRAFT, offset, size)
                        : tournamentRepository.findAllPaginated(offset, size));
        long total = statusFilter
                .map(tournamentRepository::countByStatus)
                .orElseGet(() -> excludeDrafts
                        ? tournamentRepository.countExceptStatus(TournamentStatus.DRAFT)
                        : tournamentRepository.count());
        int totalPages = (int) Math.ceil((double) total / size);
        return new Result(content, total, totalPages, page, size);
    }
}
