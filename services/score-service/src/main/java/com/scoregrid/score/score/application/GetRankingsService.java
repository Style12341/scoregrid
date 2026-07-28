package com.scoregrid.score.score.application;

import com.scoregrid.score.score.domain.model.GlobalRankingEntry;
import com.scoregrid.score.score.domain.model.MatchScore;
import com.scoregrid.score.score.domain.model.ScoredPrediction;
import com.scoregrid.score.score.domain.model.TournamentRankingEntry;
import com.scoregrid.score.score.domain.port.in.GetRankingsUseCase;
import com.scoregrid.score.score.domain.port.out.AuthClientPort;
import com.scoregrid.score.score.domain.port.out.MatchScoreRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
class GetRankingsService implements GetRankingsUseCase {

    private static final Comparator<TournamentRankingEntry> TOURNAMENT_TIEBREAK = Comparator
            .comparingInt(TournamentRankingEntry::points).reversed()
            .thenComparing(Comparator.comparingInt(TournamentRankingEntry::exactHits).reversed())
            .thenComparing(Comparator.comparingInt(TournamentRankingEntry::hits).reversed())
            .thenComparingInt(TournamentRankingEntry::predictionsScored)
            .thenComparing(TournamentRankingEntry::username);

    private static final Comparator<GlobalRankingEntry> GLOBAL_TIEBREAK = Comparator
            .comparingInt(GlobalRankingEntry::totalPoints).reversed()
            .thenComparing(Comparator.comparingInt(GlobalRankingEntry::exactHits).reversed())
            .thenComparing(Comparator.comparingInt(GlobalRankingEntry::totalHits).reversed())
            .thenComparingInt(GlobalRankingEntry::predictionsScored)
            .thenComparing(GlobalRankingEntry::username);

    private final MatchScoreRepository matchScoreRepository;
    private final AuthClientPort authClient;

    GetRankingsService(MatchScoreRepository matchScoreRepository, AuthClientPort authClient) {
        this.matchScoreRepository = matchScoreRepository;
        this.authClient = authClient;
    }

    @Override
    public List<TournamentRankingEntry> getTournamentRanking(String tournamentId, int page, int size) {
        List<MatchScore> scores = matchScoreRepository.findAllByTournamentId(tournamentId);

        Map<String, List<ScoredPrediction>> byUser = scores.stream()
                .flatMap(ms -> ms.individualScores().stream())
                .collect(Collectors.groupingBy(ScoredPrediction::userId));

        Set<String> userIds = byUser.keySet();
        Map<String, String> usernames = resolveUsernames(userIds);

        List<TournamentRankingEntry> entries = byUser.entrySet().stream()
                .map(e -> toTournamentEntry(e.getKey(), usernames.getOrDefault(e.getKey(), "?"), e.getValue()))
                .sorted(TOURNAMENT_TIEBREAK)
                .toList();

        return assignPositions(entries, page, size);
    }

    @Override
    public List<TournamentRankingEntry> getUserRanking(String userId) {
        List<MatchScore> allScores = matchScoreRepository.findAll();

        Map<String, List<ScoredPrediction>> byTournament = allScores.stream()
                .flatMap(ms -> ms.individualScores().stream()
                        .filter(sp -> sp.userId().equals(userId))
                        .map(sp -> Map.entry(ms.tournamentId(), sp)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        return byTournament.entrySet().stream()
                .map(e -> {
                    String tournamentId = e.getKey();
                    List<ScoredPrediction> userScores = e.getValue();
                    TournamentRankingEntry entry = toTournamentEntry(userId,
                            resolveUsernames(Set.of(userId)).getOrDefault(userId, userId),
                            userScores);
                    int position = findPositionInTournament(tournamentId, userId, allScores);
                    return new TournamentRankingEntry(position, entry.userId(), entry.username(),
                            entry.points(), entry.hits(), entry.exactHits(),
                            entry.predictionsScored(), entry.accuracy());
                })
                .sorted(Comparator.comparingInt(TournamentRankingEntry::position))
                .toList();
    }

    private int findPositionInTournament(String tournamentId, String userId, List<MatchScore> allScores) {
        List<MatchScore> tournamentScores = allScores.stream()
                .filter(ms -> ms.tournamentId().equals(tournamentId))
                .toList();

        Map<String, List<ScoredPrediction>> byUser = tournamentScores.stream()
                .flatMap(ms -> ms.individualScores().stream())
                .collect(Collectors.groupingBy(ScoredPrediction::userId));

        List<TournamentRankingEntry> sorted = byUser.entrySet().stream()
                .map(e -> toTournamentEntry(e.getKey(), e.getKey(), e.getValue()))
                .sorted(TOURNAMENT_TIEBREAK)
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).userId().equals(userId)) {
                return i + 1;
            }
        }
        return 0;
    }

    @Override
    public List<GlobalRankingEntry> getGlobalRanking(int page, int size) {
        List<MatchScore> allScores = matchScoreRepository.findAll();

        Map<String, List<ScoredPrediction>> byUser = allScores.stream()
                .flatMap(ms -> ms.individualScores().stream())
                .collect(Collectors.groupingBy(ScoredPrediction::userId));

        Map<String, Long> tournamentsByUser = allScores.stream()
                .flatMap(ms -> ms.individualScores().stream()
                        .map(sp -> Map.entry(sp.userId(), ms.tournamentId())))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toSet())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue().size()));

        Set<String> userIds = byUser.keySet();
        Map<String, String> usernames = resolveUsernames(userIds);

        List<GlobalRankingEntry> entries = byUser.entrySet().stream()
                .map(e -> toGlobalEntry(
                        e.getKey(),
                        usernames.getOrDefault(e.getKey(), "?"),
                        e.getValue(),
                        tournamentsByUser.getOrDefault(e.getKey(), 0L).intValue()))
                .sorted(GLOBAL_TIEBREAK)
                .toList();

        return assignGlobalPositions(entries, page, size);
    }

    private TournamentRankingEntry toTournamentEntry(String userId, String username,
                                                      List<ScoredPrediction> scores) {
        int totalPoints = scores.stream().mapToInt(ScoredPrediction::points).sum();
        int exactHits = (int) scores.stream().filter(ScoredPrediction::exactHit).count();
        int hits = (int) scores.stream().filter(ScoredPrediction::hit).count();
        int predictionsScored = scores.size();
        double accuracy = predictionsScored > 0 ? (double) hits / predictionsScored : 0.0;

        return new TournamentRankingEntry(0, userId, username, totalPoints, hits, exactHits,
                predictionsScored, accuracy);
    }

    private GlobalRankingEntry toGlobalEntry(String userId, String username,
                                              List<ScoredPrediction> scores, int tournamentsPlayed) {
        int totalPoints = scores.stream().mapToInt(ScoredPrediction::points).sum();
        int exactHits = (int) scores.stream().filter(ScoredPrediction::exactHit).count();
        int totalHits = (int) scores.stream().filter(ScoredPrediction::hit).count();
        int predictionsScored = scores.size();
        double accuracy = predictionsScored > 0 ? (double) totalHits / predictionsScored : 0.0;
        double avg = tournamentsPlayed > 0 ? (double) totalPoints / tournamentsPlayed : 0.0;

        return new GlobalRankingEntry(0, userId, username, totalPoints, tournamentsPlayed,
                totalHits, exactHits, predictionsScored, accuracy, avg);
    }

    private List<TournamentRankingEntry> assignPositions(List<TournamentRankingEntry> entries, int page, int size) {
        List<TournamentRankingEntry> positioned = IntStream.range(0, entries.size())
                .mapToObj(i -> new TournamentRankingEntry(
                        i + 1,
                        entries.get(i).userId(),
                        entries.get(i).username(),
                        entries.get(i).points(),
                        entries.get(i).hits(),
                        entries.get(i).exactHits(),
                        entries.get(i).predictionsScored(),
                        entries.get(i).accuracy()))
                .toList();

        int from = page * size;
        int to = Math.min(from + size, positioned.size());
        if (from >= positioned.size()) return List.of();
        return positioned.subList(from, to);
    }

    private List<GlobalRankingEntry> assignGlobalPositions(List<GlobalRankingEntry> entries, int page, int size) {
        List<GlobalRankingEntry> positioned = IntStream.range(0, entries.size())
                .mapToObj(i -> new GlobalRankingEntry(
                        i + 1,
                        entries.get(i).userId(),
                        entries.get(i).username(),
                        entries.get(i).totalPoints(),
                        entries.get(i).tournamentsPlayed(),
                        entries.get(i).totalHits(),
                        entries.get(i).exactHits(),
                        entries.get(i).predictionsScored(),
                        entries.get(i).accuracy(),
                        entries.get(i).averagePointsPerTournament()))
                .toList();

        int from = page * size;
        int to = Math.min(from + size, positioned.size());
        if (from >= positioned.size()) return List.of();
        return positioned.subList(from, to);
    }

    private Map<String, String> resolveUsernames(Set<String> userIds) {
        return authClient.getUsernames(userIds.stream().toList());
    }
}
