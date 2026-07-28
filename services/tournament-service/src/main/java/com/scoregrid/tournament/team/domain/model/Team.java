package com.scoregrid.tournament.team.domain.model;

/**
 * Framework-free aggregate root for a team in the global catalogue.
 */
public class Team {

    private Long id;
    private String name;
    private String shortName;
    private String country;
    private String logoUrl;

    private Team() {
    }

    public static Team create(String name, String shortName, String country, String logoUrl) {
        var t = new Team();
        t.name = name;
        t.shortName = shortName;
        t.country = country;
        t.logoUrl = logoUrl;
        return t;
    }

    public void update(String name, String shortName, String country, String logoUrl) {
        this.name = name;
        this.shortName = shortName;
        this.country = country;
        this.logoUrl = logoUrl;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getCountry() {
        return country;
    }

    public String getLogoUrl() {
        return logoUrl;
    }
}
