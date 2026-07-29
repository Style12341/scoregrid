package com.scoregrid.tournament.match.domain.port.in;

import com.scoregrid.tournament.match.domain.model.Match;

public interface GetMatch {
    Match execute(Long id);
}
