package com.example.footballbooking.dto;

import java.time.Instant;
import java.util.List;

public record MatchRequest(String homeTeam, String awayTeam, String stadium,
                           Instant startTime, String description, String organizer,
                           List<TicketTypeRequest> ticketTypes) {
}
