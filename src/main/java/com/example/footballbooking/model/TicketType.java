package com.example.footballbooking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ticket_types")
public class TicketType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private BigDecimal price;
    private int totalQuantity;
    private int allocatedQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id")
    private Event match;

    protected TicketType() {
    }

    TicketType(Event match, String name, BigDecimal price, int totalQuantity) {
        this.match = match;
        this.name = name;
        this.price = price;
        this.totalQuantity = totalQuantity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getAvailableQuantity() { return totalQuantity - allocatedQuantity; }

    public long matchId() { return match.getId(); }
    public int allocatedQuantity() { return allocatedQuantity; }

    public void updateDetails(String name, BigDecimal price, int totalQuantity) {
        this.name = name;
        this.price = price;
        this.totalQuantity = totalQuantity;
    }
}
