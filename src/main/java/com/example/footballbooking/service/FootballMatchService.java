package com.example.footballbooking.service;

import com.example.footballbooking.dto.MatchRequest;
import com.example.footballbooking.dto.TicketTypeRequest;
import com.example.footballbooking.model.FootballMatch;
import com.example.footballbooking.model.TicketType;
import java.time.Instant;
import java.util.List;

public interface FootballMatchService {
    record MatchPage(List<FootballMatch> content, int page, int size, long totalElements, int totalPages) {}

    List<FootballMatch> getMatches();
    MatchPage search(String keyword, String venue, Instant from, Instant to, int page, int size);
    FootballMatch getMatch(Long id);
    FootballMatch createMatch(MatchRequest request);
    FootballMatch updateMatch(Long id, MatchRequest request);
    TicketType addTicketType(Long matchId, TicketTypeRequest request);
}
