package com.example.footballbooking.controller;

import com.example.footballbooking.dto.MatchRequest;
import com.example.footballbooking.dto.TicketTypeRequest;
import com.example.footballbooking.model.FootballMatch;
import com.example.footballbooking.model.TicketType;
import com.example.footballbooking.service.FootballMatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
public class FootballMatchController {
    private final FootballMatchService service;

    public FootballMatchController(FootballMatchService service) {
        this.service = service;
    }

    // Giu /football cho bai tap cu; /api/events theo duong dan trong de bai.
    @GetMapping("/football")
    public List<FootballMatch> getMatches() {
        return service.getMatches();
    }

    @GetMapping("/api/events")
    public Object getEvents(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String venue,
                            @RequestParam(required = false) Instant from,
                            @RequestParam(required = false) Instant to,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        if (keyword == null && venue == null && from == null && to == null && page == null && size == null) {
            return service.getMatches();
        }
        return service.search(keyword, venue, from, to, page == null ? 0 : page, size == null ? 10 : size);
    }

    @GetMapping("/api/events/{id}")
    public FootballMatch getMatch(@PathVariable("id") Long id) {
        return service.getMatch(id);
    }

    @PostMapping("/api/events")
    public ResponseEntity<FootballMatch> createMatch(@RequestBody MatchRequest request) {
        FootballMatch match = service.createMatch(request);
        return ResponseEntity.created(URI.create("/api/events/" + match.getId())).body(match);
    }

    @PutMapping("/api/events/{id}")
    public FootballMatch updateMatch(@PathVariable("id") Long id, @RequestBody MatchRequest request) {
        return service.updateMatch(id, request);
    }

    @PostMapping("/api/events/{id}/ticket-types")
    public ResponseEntity<TicketType> addTicketType(@PathVariable("id") Long id,
                                                   @RequestBody TicketTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTicketType(id, request));
    }
}
