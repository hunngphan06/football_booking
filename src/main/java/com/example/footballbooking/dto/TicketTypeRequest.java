package com.example.footballbooking.dto;

import java.math.BigDecimal;

public record TicketTypeRequest(String name, BigDecimal price, Integer totalQuantity) {
}
