package com.example.footballbooking.service;

import com.example.footballbooking.dto.EventRequest;
import com.example.footballbooking.dto.TicketTypeRequest;
import com.example.footballbooking.model.Event;
import com.example.footballbooking.model.TicketType;
import java.time.Instant;
import java.util.List;

public interface EventService {
    record EventPage(List<Event> content, int page, int size, long totalElements, int totalPages) {}

    List<Event> list(String category);
    EventPage search(String keyword, String category, String venue, Instant from, Instant to, int page, int size);
    Event getEvent(Long id);
    Event createEvent(EventRequest request);
    Event updateEvent(Long id, EventRequest request);
    void deleteEvent(Long id);
    TicketType addTicketType(Long matchId, TicketTypeRequest request);
    TicketType updateTicketType(Long matchId, Long ticketId, TicketTypeRequest request);
}
