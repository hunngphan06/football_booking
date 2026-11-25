package com.example.footballbooking.repository;

import com.example.footballbooking.model.Event;
import com.example.footballbooking.model.TicketType;
import java.math.BigDecimal;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventRepository extends JpaRepository<Event, Long>,
        JpaSpecificationExecutor<Event> {
    @EntityGraph(attributePaths = "ticketTypes")
    List<Event> findAllByOrderByIdAsc();

    @Override
    @EntityGraph(attributePaths = "ticketTypes")
    Optional<Event> findById(Long id);

    @EntityGraph(attributePaths = "ticketTypes")
    List<Event> findAllByCategoryOrderByIdAsc(String category);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findForUpdate(Long id);

    default Page<Event> search(String keyword, String category, String venue, Instant from, Instant to,
                                       BigDecimal maxPrice, Pageable pageable) {
        return findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (category != null) {
                filters.add(cb.equal(root.get("category"), category));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = containsPattern(keyword);
                filters.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '!'),
                    cb.like(cb.lower(root.get("homeTeam")), pattern, '!'),
                    cb.like(cb.lower(root.get("awayTeam")), pattern, '!'),
                    cb.like(cb.lower(root.get("description")), pattern, '!')));
            }
            if (venue != null && !venue.isBlank()) {
                filters.add(cb.like(cb.lower(root.get("venue")), containsPattern(venue), '!'));
            }
            if (from != null) {
                filters.add(cb.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (to != null) {
                filters.add(cb.lessThan(root.get("startTime"), to));
            }
            if (maxPrice != null) {
                var tickets = query.subquery(Long.class);
                var ticket = tickets.from(TicketType.class);
                tickets.select(ticket.get("id")).where(
                    cb.equal(ticket.get("match"), root),
                    cb.le(ticket.get("price"), maxPrice),
                    cb.greaterThan(cb.diff(ticket.get("totalQuantity"), ticket.get("allocatedQuantity")), 0));
                filters.add(cb.exists(tickets));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, pageable);
    }

    private static String containsPattern(String input) {
        return "%" + input.strip().toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

}
