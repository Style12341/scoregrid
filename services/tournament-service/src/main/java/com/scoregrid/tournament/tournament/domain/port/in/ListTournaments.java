package com.scoregrid.tournament.tournament.domain.port.in;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;

import java.util.List;
import java.util.Optional;

public interface ListTournaments {
    record Result(List<Tournament> content, long totalElements, int totalPages, int number, int size) {}

    Result execute(Optional<TournamentStatus> statusFilter, int page, int size);
}
