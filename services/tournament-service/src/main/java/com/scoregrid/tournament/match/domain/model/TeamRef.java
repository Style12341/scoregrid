package com.scoregrid.tournament.match.domain.model;

/**
 * Lightweight value object referencing a team in the catalogue.
 *
 * <p>Used by the {@link Match} domain model instead of embedding the full Team
 * aggregate. Immutable.
 */
public class TeamRef {

    private final Long id;
    private final String name;
    private final String shortName;

    private TeamRef(Long id, String name, String shortName) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
    }

    public static TeamRef of(Long id, String name, String shortName) {
        return new TeamRef(id, name, shortName);
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String shortName() {
        return shortName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamRef other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "TeamRef{id=" + id + ", name='" + name + "'}";
    }
}
