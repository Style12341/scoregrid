package com.scoregrid.tournament.phase.domain.model;

/**
 * Framework-free domain model for a tournament phase.
 *
 * <p>No Spring annotations, no JPA — this class would compile without any
 * framework jar on the classpath.
 */
public class Phase {

    private Long id;
    private Long tournamentId;
    private String name;       // nullable
    private PhaseType type;
    private int displayOrder;

    private Phase() {
    }

    public static Phase create(Long tournamentId, PhaseType type, String name, int displayOrder) {
        var p = new Phase();
        p.tournamentId = tournamentId;
        p.type = type;
        p.name = name;
        p.displayOrder = displayOrder;
        return p;
    }

    /**
     * Reconstitutes a phase from persistence without running validation.
     * Only for use by persistence adapters during read operations.
     */
    public static Phase reconstitute(Long id, Long tournamentId, String name,
                                     PhaseType type, int displayOrder) {
        var p = new Phase();
        p.id = id;
        p.tournamentId = tournamentId;
        p.name = name;
        p.type = type;
        p.displayOrder = displayOrder;
        return p;
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

    public String getName() {
        return name;
    }

    public PhaseType getType() {
        return type;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
