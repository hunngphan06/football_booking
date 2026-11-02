package com.example.footballbooking.service;

import com.example.footballbooking.exception.MatchNotFoundException;
import com.example.footballbooking.model.FootballMatch;
import com.example.footballbooking.repository.FootballMatchRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FootballMatchService {
    private final FootballMatchRepository repository;

    
    public FootballMatchService(FootballMatchRepository repository) {
        this.repository = repository;
    }

    public List<FootballMatch> getMatches() {
        return repository.findAll();
    }

    public FootballMatch getMatch(Long id) {
        return repository.findById(id).orElseThrow(() -> new MatchNotFoundException(id));
    }
}
