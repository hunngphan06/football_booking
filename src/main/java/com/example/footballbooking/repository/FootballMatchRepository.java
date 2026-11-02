package com.example.footballbooking.repository;

import com.example.footballbooking.model.FootballMatch;
import java.util.List;
import java.util.Optional;

public interface FootballMatchRepository {
    List<FootballMatch> findAll();
    Optional<FootballMatch> findById(Long id);
}
