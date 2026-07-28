package com.scoregrid.score.score.domain.port.in;

import com.scoregrid.score.score.domain.model.GlobalRankingEntry;
import com.scoregrid.score.score.domain.model.TournamentRankingEntry;

import java.util.List;

public interface GetRankingsUseCase {

    List<TournamentRankingEntry> getTournamentRanking(String tournamentId, int page, int size);

    List<GlobalRankingEntry> getGlobalRanking(int page, int size);

    List<TournamentRankingEntry> getUserRanking(String userId);
}
