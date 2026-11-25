package com.example.footballbooking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String homeTeam;
    private String awayTeam;
    @Column(name = "stadium")
    private String venue;
    private String title;
    private String category;
    private boolean saleOpen;
    private Instant startTime;
    private String description;
    private String organizer;

    @OneToMany(mappedBy = "match", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<TicketType> ticketTypes = new ArrayList<>();

    public Event() {
    }

    public void updateDetails(String title, String category, String venue, String homeTeam, String awayTeam,
                              Instant startTime, String description, String organizer, boolean saleOpen) {
        this.title = title;
        this.category = category;
        this.venue = venue;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.saleOpen = saleOpen;
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
    public String getTitle() { return title; }
    public String getHomeTeam() { return homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public String getVenue() { return venue; }
    public String getStadium() { return venue; }
    public Instant getStartTime() { return startTime; }
    public String getCategory() { return category; }
    public boolean isSaleOpen() { return saleOpen; }
    public String getDescription() { return description; }
    public String getOrganizer() { return organizer; }
    public List<TicketType> getTicketTypes() { return List.copyOf(ticketTypes); }
}
