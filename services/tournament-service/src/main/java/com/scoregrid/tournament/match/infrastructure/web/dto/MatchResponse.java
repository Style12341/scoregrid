package com.scoregrid.tournament.match.infrastructure.web.dto;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record MatchResponse(
        String id,
        String tournamentId,
        String groupId,
        String phaseId,
        Map<String, String> homeTeam,
        Map<String, String> awayTeam,
        String startTime,
        String status,
        Integer homeScore,
        Integer awayScore,
        boolean predictionsOpen
) {
    public static MatchResponse from(Match match) {
        Instant now = Instant.now();
        return from(match, now);
    }

    /**
     * Computes the response from a match domain object using the given clock.
     *
     * <p>{@code predictionsOpen} is computed here, not stored in the domain model.
     * Effective status: stored SCHEDULED + now >= startTime shows as IN_PROGRESS.
     */
    public static MatchResponse from(Match match, Instant now) {
        boolean effectiveInProgress = match.isInProgress(now);
        MatchStatus displayStatus = effectiveInProgress ? MatchStatus.IN_PROGRESS : match.getStatus();
        boolean predictionsOpen = match.isPredictionsOpen(now);

        return new MatchResponse(
                match.getId().toString(),
                match.getTournamentId().toString(),
                match.getGroupId() != null ? match.getGroupId().toString() : null,
                match.getPhaseId() != null ? match.getPhaseId().toString() : null,
                teamReference(match.getHomeTeam().id(), match.getHomeTeam().name(),
                        match.getHomeTeam().shortName()),
                teamReference(match.getAwayTeam().id(), match.getAwayTeam().name(),
                        match.getAwayTeam().shortName()),
                match.getStartTime().toString(),
                displayStatus.name(),
                match.getHomeScore(),
                match.getAwayScore(),
                predictionsOpen);
    }

    private static Map<String, String> teamReference(Long id, String name, String shortName) {
        var reference = new LinkedHashMap<String, String>();
        reference.put("id", id.toString());
        reference.put("name", name);
        reference.put("shortName", shortName);
        return reference;
    }
}
