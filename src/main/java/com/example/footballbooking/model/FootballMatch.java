package com.example.footballbooking.model;

import java.time.Instant;
import java.util.List;

/** Thong tin mot tran dau. Chua phai JPA Entity va chua luu database. */
public class FootballMatch {
    private final Long id;
    private final String title;
    private final String homeTeam;
    private final String awayTeam;
    private final String stadium;
    private final Instant startTime;
    private final String description;
    private final String organizer;
    private final List<TicketType> ticketTypes;

    public FootballMatch(Long id, String homeTeam, String awayTeam, String stadium,
                         Instant startTime, String description, String organizer,
                         List<TicketType> ticketTypes) {
        this.id = id;
        this.title = homeTeam + " vs " + awayTeam;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.stadium = stadium;
        this.startTime = startTime;
        this.description = description;
        this.organizer = organizer;
        this.ticketTypes = List.copyOf(ticketTypes);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getHomeTeam() { return homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public String getStadium() { return stadium; }
    public Instant getStartTime() { return startTime; }
    public String getCategory() { return "SPORTS"; }
    public String getDescription() { return description; }
    public String getOrganizer() { return organizer; }
    public List<TicketType> getTicketTypes() { return ticketTypes; }
}
