package com.scoregrid.tournament.match.domain.model;

import java.time.Instant;

/**
 * Framework-free aggregate root for a match.
 *
 * <p>No Spring annotations, no JPA — this class would compile without any
 * framework jar on the classpath.
 *
 * <p>Invariants enforced by the {@code create(...)} factory:
 * <ul>
 *   <li>homeTeam must differ from awayTeam</li>
 *   <li>exactly one of groupId / phaseId is non-null</li>
 *   <li>startTime must be strictly in the future (checked against caller-supplied now)</li>
 * </ul>
 */
public class Match {

    private Long id;
    private Long tournamentId;
    private Long groupId;       // nullable; exactly one of groupId/phaseId required
    private Long phaseId;       // nullable
    private TeamRef homeTeam;
    private TeamRef awayTeam;
    private Instant startTime;
    private MatchStatus status;
    private Integer homeScore;  // nullable
    private Integer awayScore;  // nullable

    private Match() {
    }

    // -- factory ---------------------------------------------------------------

    /**
     * Creates a new match enforcing all invariants.
     *
     * @param now the current instant (caller-supplied) to check future startTime
     * @throws IllegalArgumentException on any invariant violation
     */
    public static Match create(Long tournamentId, Long groupId, Long phaseId,
                                TeamRef homeTeam, TeamRef awayTeam,
                                Instant startTime, Instant now) {
        if (homeTeam.id().equals(awayTeam.id())) {
            throw new IllegalArgumentException("Home team and away team must differ");
        }
        if ((groupId == null) == (phaseId == null)) {
            throw new IllegalArgumentException("Exactly one of groupId or phaseId is required");
        }
        if (!startTime.isAfter(now)) {
            throw new IllegalArgumentException("startTime must be in the future");
        }

        var m = new Match();
        m.tournamentId = tournamentId;
        m.groupId = groupId;
        m.phaseId = phaseId;
        m.homeTeam = homeTeam;
        m.awayTeam = awayTeam;
        m.startTime = startTime;
        m.status = MatchStatus.SCHEDULED;
        return m;
    }

    /**
     * Reconstitutes a match from persistence without running validation.
     * Only for use by persistence adapters during read operations.
     */
    public static Match reconstitute(Long id, Long tournamentId, Long groupId, Long phaseId,
                                      TeamRef homeTeam, TeamRef awayTeam,
                                      Instant startTime, MatchStatus status,
                                      Integer homeScore, Integer awayScore) {
        var m = new Match();
        m.id = id;
        m.tournamentId = tournamentId;
        m.groupId = groupId;
        m.phaseId = phaseId;
        m.homeTeam = homeTeam;
        m.awayTeam = awayTeam;
        m.startTime = startTime;
        m.status = status;
        m.homeScore = homeScore;
        m.awayScore = awayScore;
        return m;
    }

    // -- state machine transitions ---------------------------------------------

    public void start() {
        validateTransition(MatchStatus.IN_PROGRESS);
        this.status = MatchStatus.IN_PROGRESS;
    }

    public void finish() {
        validateTransition(MatchStatus.FINISHED);
        this.status = MatchStatus.FINISHED;
    }

    public void postpone() {
        validateTransition(MatchStatus.POSTPONED);
        this.status = MatchStatus.POSTPONED;
    }

    public void cancel() {
        validateTransition(MatchStatus.CANCELLED);
        this.status = MatchStatus.CANCELLED;
    }

    public void reschedule(Instant newStartTime) {
        reschedule(newStartTime, Instant.now());
    }

    public void reschedule(Instant newStartTime, Instant now) {
        validateTransition(MatchStatus.SCHEDULED);
        validateFutureStartTime(newStartTime, now);
        this.startTime = newStartTime;
        this.status = MatchStatus.SCHEDULED;
    }

    public void changeStartTime(Instant newStartTime, Instant now) {
        if (this.status == MatchStatus.SCHEDULED) {
            validateFutureStartTime(newStartTime, now);
        }
        this.startTime = newStartTime;
    }

    /**
     * Loads a match result, derives the outcome, and transitions to FINISHED.
     *
     * @throws IllegalStateException if the match is in a non-finishable state
     */
    public String loadResult(int homeScore, int awayScore) {
        if (homeScore < 0 || homeScore > 99 || awayScore < 0 || awayScore > 99) {
            throw new IllegalArgumentException("Scores must be between 0 and 99");
        }
        if (!canFinish()) {
            throw new IllegalStateException(
                    "Cannot load result for a match in status " + this.status);
        }
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.status = MatchStatus.FINISHED;
        return deriveOutcome();
    }

    private void validateTransition(MatchStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid transition: " + this.status + " → " + target);
        }
    }

    private static void validateFutureStartTime(Instant startTime, Instant now) {
        if (startTime == null || now == null || !startTime.isAfter(now)) {
            throw new IllegalArgumentException("startTime must be in the future");
        }
    }

    private boolean canFinish() {
        return this.status == MatchStatus.SCHEDULED
                || this.status == MatchStatus.IN_PROGRESS
                || this.status == MatchStatus.FINISHED;
    }

    // -- outcome derivation ----------------------------------------------------

    /**
     * Derives the match outcome from stored scores.
     * Returns null if scores are not set.
     */
    public String outcome() {
        if (homeScore == null || awayScore == null) return null;
        return deriveOutcome();
    }

    private String deriveOutcome() {
        if (homeScore > awayScore) return "HOME_WIN";
        if (homeScore < awayScore) return "AWAY_WIN";
        return "DRAW";
    }

    // -- view helpers ----------------------------------------------------------

    /**
     * True when the stored status is SCHEDULED and kickoff has passed.
     */
    public boolean isInProgress(Instant now) {
        return this.status == MatchStatus.SCHEDULED && !now.isBefore(startTime);
    }

    /**
     * True when predictions are open: status is SCHEDULED and kickoff is in the future.
     */
    public boolean isPredictionsOpen(Instant now) {
        return this.status == MatchStatus.SCHEDULED && now.isBefore(startTime);
    }

    // -- direct field mutation (for admin updates) -----------------------------

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public void setPhaseId(Long phaseId) {
        this.phaseId = phaseId;
    }

    public void setHomeTeam(TeamRef homeTeam) {
        this.homeTeam = homeTeam;
    }

    public void setAwayTeam(TeamRef awayTeam) {
        this.awayTeam = awayTeam;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    // -- accessors -------------------------------------------------------------

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getPhaseId() {
        return phaseId;
    }

    public TeamRef getHomeTeam() {
        return homeTeam;
    }

    public TeamRef getAwayTeam() {
        return awayTeam;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Integer getHomeScore() {
        return homeScore;
    }

    public Integer getAwayScore() {
        return awayScore;
    }
}
