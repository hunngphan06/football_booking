package com.example.footballbooking.service;

import com.example.footballbooking.dto.MatchRequest;
import com.example.footballbooking.dto.TicketTypeRequest;
import com.example.footballbooking.exception.MatchNotFoundException;
import com.example.footballbooking.exception.InvalidRequestException;
import com.example.footballbooking.model.FootballMatch;
import com.example.footballbooking.model.TicketType;
import com.example.footballbooking.repository.FootballMatchRepository;
import com.example.footballbooking.repository.TicketTypeRepository;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FootballMatchServiceImpl implements FootballMatchService {
    private final FootballMatchRepository repository;
    private final TicketTypeRepository ticketTypes;

    public FootballMatchServiceImpl(FootballMatchRepository repository, TicketTypeRepository ticketTypes) {
        this.repository = repository;
        this.ticketTypes = ticketTypes;
    }

    @Override
    public List<FootballMatch> getMatches() {
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public MatchPage search(String keyword, String venue, Instant from, Instant to, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidRequestException("page must be nonnegative and size must be between 1 and 50");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidRequestException("from must be earlier than to");
        }
        var result = repository.search(keyword, venue, from, to, PageRequest.of(page, size, Sort.by("id")));
        for (FootballMatch match : result.getContent()) {
            match.getTicketTypes();
        }
        return new MatchPage(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public FootballMatch getMatch(Long id) {
        return repository.findById(id).orElseThrow(() -> new MatchNotFoundException(id));
    }

    @Override
    @Transactional
    public FootballMatch createMatch(MatchRequest request) {
        validateMatch(request);
        FootballMatch match = new FootballMatch(request.homeTeam().trim(), request.awayTeam().trim(),
            request.stadium().trim(), request.startTime(), request.description(), request.organizer().trim());
        if (request.ticketTypes() != null) {
            for (TicketTypeRequest ticket : request.ticketTypes()) {
                addValidatedTicket(match, ticket);
            }
        }
        return repository.save(match);
    }

    @Override
    @Transactional
    public FootballMatch updateMatch(Long id, MatchRequest request) {
        validateMatch(request);
        if (request.ticketTypes() != null) {
            throw new InvalidRequestException("Use the ticket-types endpoint to add ticket types");
        }
        FootballMatch match = getMatch(id);
        match.updateDetails(request.homeTeam().trim(), request.awayTeam().trim(), request.stadium().trim(),
            request.startTime(), request.description(), request.organizer().trim());
        return match;
    }

    @Override
    @Transactional
    public TicketType addTicketType(Long matchId, TicketTypeRequest request) {
        TicketType ticket = addValidatedTicket(getMatch(matchId), request);
        return ticketTypes.save(ticket);
    }

    private void validateMatch(MatchRequest request) {
        validateText(request.homeTeam(), "homeTeam");
        validateText(request.awayTeam(), "awayTeam");
        validateText(request.stadium(), "stadium");
        validateText(request.organizer(), "organizer");
        if (request.startTime() == null) {
            throw new InvalidRequestException("startTime is required");
        }
    }

    private TicketType addValidatedTicket(FootballMatch match, TicketTypeRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Each ticket type must be an object");
        }
        validateText(request.name(), "name");
        if (request.price() == null || request.price().signum() < 0
                || request.price().stripTrailingZeros().scale() > 2
                || request.price().compareTo(new java.math.BigDecimal("1000000000000")) >= 0) {
            throw new InvalidRequestException("price must be nonnegative, below 1000000000000, and have at most two decimal places");
        }
        if (request.totalQuantity() == null || request.totalQuantity() <= 0) {
            throw new InvalidRequestException("totalQuantity must be a positive integer");
        }
        return match.addTicketType(request.name().trim(), request.price().setScale(2, RoundingMode.UNNECESSARY),
            request.totalQuantity());
    }

    private void validateText(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 255) {
            throw new InvalidRequestException(field + " must contain between 1 and 255 characters");
        }
    }
}
