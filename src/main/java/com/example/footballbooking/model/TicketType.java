package com.example.footballbooking.model;

import java.math.BigDecimal;

public class TicketType {
    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final int totalQuantity;

    public TicketType(Long id, String name, BigDecimal price, int totalQuantity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.totalQuantity = totalQuantity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public int getTotalQuantity() { return totalQuantity; }
}
