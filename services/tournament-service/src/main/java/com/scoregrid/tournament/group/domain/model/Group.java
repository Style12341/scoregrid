package com.scoregrid.tournament.group.domain.model;

/**
 * Framework-free domain model for a tournament group.
 *
 * <p>No Spring annotations, no JPA — this class would compile without any
 * framework jar on the classpath.
 */
public class Group {

    private Long id;
    private Long tournamentId;
    private String name;
    private int displayOrder;

    private Group() {
    }

    public static Group create(Long tournamentId, String name, int displayOrder) {
        var g = new Group();
        g.tournamentId = tournamentId;
        g.name = name;
        g.displayOrder = displayOrder;
        return g;
    }

    /**
     * Reconstitutes a group from persistence without running validation.
     * Only for use by persistence adapters during read operations.
     */
    public static Group reconstitute(Long id, Long tournamentId, String name, int displayOrder) {
        var g = new Group();
        g.id = id;
        g.tournamentId = tournamentId;
        g.name = name;
        g.displayOrder = displayOrder;
        return g;
    }

    // -- accessors (internal ids for persistence) ------------------------------

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

    public int getDisplayOrder() {
        return displayOrder;
    }
}
