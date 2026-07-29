package com.scoregrid.tournament.match.application;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.port.in.GetMatch;
import com.scoregrid.tournament.match.domain.port.out.MatchRepository;
import com.scoregrid.tournament.shared.error.DomainException;
import com.scoregrid.tournament.shared.error.ErrorKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetMatchUseCase implements GetMatch {

    private final MatchRepository matchRepository;

    public GetMatchUseCase(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @Override
    public Match execute(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "NOT_FOUND",
                        "Match not found: " + id));
    }
}
