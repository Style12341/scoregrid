package com.scoregrid.tournament.tournament.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Framework-free aggregate root for a tournament.
 *
 * <p>No Spring annotations, no JPA — this class would compile without any
 * framework jar on the classpath. The boundary is real.
 */
public class Tournament {

    private Long id;
    private String name;
    private String description;
    private TournamentStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    private Tournament() {
    }

    public static Tournament create(String name, String description,
                                     LocalDate startDate, LocalDate endDate, String createdBy) {
        var t = new Tournament();
        t.name = name;
        t.description = description;
        t.status = TournamentStatus.DRAFT;
        t.startDate = startDate;
        t.endDate = endDate;
        t.createdBy = createdBy;
        t.createdAt = Instant.now();
        t.updatedAt = t.createdAt;
        return t;
    }

    /**
     * Reconstitutes a tournament from persistence without running the state
     * machine. Only for use by persistence adapters during read operations.
     */
    public static Tournament reconstitute(Long id, String name, String description,
                                          TournamentStatus status,
                                          LocalDate startDate, LocalDate endDate,
                                          String createdBy,
                                          Instant createdAt, Instant updatedAt) {
        var t = new Tournament();
        t.id = id;
        t.name = name;
        t.description = description;
        t.status = status;
        t.startDate = startDate;
        t.endDate = endDate;
        t.createdBy = createdBy;
        t.createdAt = createdAt;
        t.updatedAt = updatedAt;
        return t;
    }

    // -- state machine ---------------------------------------------------------

    public void transitionTo(TournamentStatus target) {
        if (target == this.status) {
            throw new IllegalStateException("Tournament is already " + target);
        }
        if (!isValidTransition(this.status, target)) {
            throw new IllegalStateException(
                    "Invalid transition: " + this.status + " → " + target);
        }
        if (target == TournamentStatus.ACTIVE) {
            validateActivation();
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    private static boolean isValidTransition(TournamentStatus from, TournamentStatus to) {
        return switch (from) {
            case DRAFT -> to == TournamentStatus.ACTIVE;
            case ACTIVE -> to == TournamentStatus.FINISHED || to == TournamentStatus.CANCELLED;
            case FINISHED, CANCELLED -> false;
        };
    }

    private void validateActivation() {
        if (startDate == null) {
            throw new IllegalArgumentException("Cannot activate: startDate is required.");
        }
        if (!startDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot activate: startDate must be in the future.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Cannot activate: endDate must be on or after startDate.");
        }
    }

    // -- updates ---------------------------------------------------------------

    public void update(String name, String description, LocalDate startDate, LocalDate endDate) {
        if (this.status == TournamentStatus.FINISHED || this.status == TournamentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot update a " + this.status + " tournament.");
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (this.status == TournamentStatus.DRAFT) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
        // ACTIVE: only name and description are editable — dates are silently ignored.
        this.updatedAt = Instant.now();
    }

    // -- deletion guard --------------------------------------------------------

    public boolean canBeDeleted() {
        return this.status == TournamentStatus.DRAFT;
    }

    // -- accessors (internal ids for persistence) ------------------------------

    public void setId(Long id) {
        this.id = id;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
