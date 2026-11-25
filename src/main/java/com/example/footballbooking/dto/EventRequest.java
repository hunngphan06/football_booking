package com.example.footballbooking.dto;

import java.time.Instant;
import java.util.List;

public record EventRequest(String homeTeam, String awayTeam, String stadium,
                           Instant startTime, String description, String organizer,
                           List<TicketTypeRequest> ticketTypes, String title, String venue,
                           String category, Boolean saleOpen) {
}
