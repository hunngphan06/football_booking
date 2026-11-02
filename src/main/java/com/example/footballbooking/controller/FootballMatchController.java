package com.example.footballbooking.controller;

import com.example.footballbooking.model.FootballMatch;
import com.example.footballbooking.service.FootballMatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class FootballMatchController {
    private final FootballMatchService service;

    public FootballMatchController(FootballMatchService service) {
        this.service = service;
    }

    // Giu /football cho bai tap cu; /api/events theo duong dan trong de bai.
    @GetMapping({"/api/events", "/football"})
    public List<FootballMatch> getMatches() {
        return service.getMatches();
    }

    @GetMapping("/api/events/{id}")
    public FootballMatch getMatch(@PathVariable("id") Long id) {
        return service.getMatch(id);
    }
}
