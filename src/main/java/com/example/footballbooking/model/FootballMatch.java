package com.example.footballbooking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "football_matches")
public class FootballMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String homeTeam;
    private String awayTeam;
    private String stadium;
    private Instant startTime;
    private String description;
    private String organizer;

    @OneToMany(mappedBy = "match", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<TicketType> ticketTypes = new ArrayList<>();

    protected FootballMatch() {
    }

    public FootballMatch(String homeTeam, String awayTeam, String stadium,
                         Instant startTime, String description, String organizer) {
        updateDetails(homeTeam, awayTeam, stadium, startTime, description, organizer);
    }

    public void updateDetails(String homeTeam, String awayTeam, String stadium,
                              Instant startTime, String description, String organizer) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.stadium = stadium;
        this.startTime = startTime;
        this.description = description;
        this.organizer = organizer;
    }

    public TicketType addTicketType(String name, BigDecimal price, int totalQuantity) {
        TicketType ticketType = new TicketType(this, name, price, totalQuantity);
        ticketTypes.add(ticketType);
        return ticketType;
    }

    public Long getId() { return id; }
    public String getTitle() { return homeTeam + " vs " + awayTeam; }
    public String getHomeTeam() { return homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public String getStadium() { return stadium; }
    public Instant getStartTime() { return startTime; }
    public String getCategory() { return "SPORTS"; }
    public String getDescription() { return description; }
    public String getOrganizer() { return organizer; }
    public List<TicketType> getTicketTypes() { return List.copyOf(ticketTypes); }
}
