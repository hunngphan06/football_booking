package com.example.footballbooking.repository;

import com.example.footballbooking.model.FootballMatch;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FootballMatchRepository extends JpaRepository<FootballMatch, Long>,
        JpaSpecificationExecutor<FootballMatch> {
    @EntityGraph(attributePaths = "ticketTypes")
    List<FootballMatch> findAllByOrderByIdAsc();

    @Override
    @EntityGraph(attributePaths = "ticketTypes")
    Optional<FootballMatch> findById(Long id);

    default Page<FootballMatch> search(String keyword, String venue, Instant from, Instant to, Pageable pageable) {
        return findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = containsPattern(keyword);
                filters.add(cb.or(cb.like(cb.lower(root.get("homeTeam")), pattern, '!'),
                    cb.like(cb.lower(root.get("awayTeam")), pattern, '!'),
                    cb.like(cb.lower(root.get("description")), pattern, '!')));
            }
            if (venue != null && !venue.isBlank()) {
                filters.add(cb.like(cb.lower(root.get("stadium")), containsPattern(venue), '!'));
            }
            if (from != null) {
                filters.add(cb.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (to != null) {
                filters.add(cb.lessThan(root.get("startTime"), to));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, pageable);
    }

    private static String containsPattern(String input) {
        return "%" + input.strip().toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

}
