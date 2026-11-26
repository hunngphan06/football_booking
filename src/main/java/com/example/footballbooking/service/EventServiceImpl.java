package com.example.footballbooking.service;

import com.example.footballbooking.dto.EventRequest;
import com.example.footballbooking.dto.TicketTypeRequest;
import com.example.footballbooking.exception.EventNotFoundException;
import com.example.footballbooking.exception.InvalidRequestException;
import com.example.footballbooking.exception.BookingException;
import org.springframework.http.HttpStatus;
import com.example.footballbooking.model.Event;
import com.example.footballbooking.model.EventCover;
import com.example.footballbooking.model.TicketType;
import com.example.footballbooking.repository.EventRepository;
import com.example.footballbooking.repository.EventCoverRepository;
import com.example.footballbooking.repository.BookingRepository;
import com.example.footballbooking.repository.EventCategoryRepository;
import java.util.Locale;
import java.util.UUID;
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
public class EventServiceImpl implements EventService {
    private final EventRepository repository;
    private final TicketTypeRepository ticketTypes;

    private final EventCategoryRepository categories;
    private final BookingRepository bookings;
    private final EventCoverRepository covers;

    public EventServiceImpl(EventRepository repository, TicketTypeRepository ticketTypes,
                            EventCategoryRepository categories, BookingRepository bookings, EventCoverRepository covers) {
        this.repository = repository;
        this.ticketTypes = ticketTypes;
        this.categories = categories;
        this.bookings = bookings;
        this.covers = covers;
    }

    @Override
    public List<Event> list(String category) {
        String code = categoryCode(category);
        return code == null ? repository.findAllByOrderByIdAsc() : repository.findAllByCategoryOrderByIdAsc(code);
    }

    @Override
    public EventPage search(String keyword, String category, String venue, Instant from, Instant to, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidRequestException("page must be nonnegative and size must be between 1 and 50");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidRequestException("from must be earlier than to");
        }
        var result = repository.search(keyword, categoryCode(category), venue, from, to, null, PageRequest.of(page, size, Sort.by("id")));
        for (Event match : result.getContent()) {
            match.getTicketTypes();
        }
        return new EventPage(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public Event getEvent(Long id) {
        return repository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    @Override
    @Transactional
    public Event createEvent(EventRequest request, EventCover cover) {
        Event match = new Event();
        applyDetails(match, request);
        if (request.ticketTypes() != null) {
            for (TicketTypeRequest ticket : request.ticketTypes()) {
                addValidatedTicket(match, ticket);
            }
        }
        Event event = repository.save(match);
        if (cover != null) {
            saveCover(event, cover);
        }
        return event;
    }

    @Override
    @Transactional
    public Event updateEvent(Long id, EventRequest request, EventCover cover, boolean removeCover) {
        if (request.ticketTypes() != null) {
            throw new InvalidRequestException("Use the ticket-types endpoint to add ticket types");
        }
        if (cover != null && removeCover) {
            throw new InvalidRequestException("Chọn thay ảnh hoặc gỡ ảnh.");
        }
        Event match = lockEvent(id);
        applyDetails(match, request);
        if (cover != null) {
            saveCover(match, cover);
        } else if (removeCover) {
            covers.delete(id);
            match.changeCover(null);
        }
        match.getTicketTypes();
        return match;
    }

    @Override
    public EventCover getCover(long id) {
        return covers.find(id).orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "Ảnh bìa không tồn tại."));
    }

    private void saveCover(Event event, EventCover cover) {
        covers.save(event.getId(), cover);
        event.changeCover(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public TicketType addTicketType(Long matchId, TicketTypeRequest request) {
        TicketType ticket = addValidatedTicket(lockEvent(matchId), request);
        return ticketTypes.save(ticket);
    }

    @Override
    @Transactional
    public void deleteEvent(Long id) {
        Event event = lockEvent(id);
        if (bookings.hasBookings(id)) {
            throw new BookingException(HttpStatus.CONFLICT, "This event has bookings. Close ticket sales instead.");
        }
        repository.delete(event);
    }

    private Event lockEvent(Long id) {
        return repository.findForUpdate(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    private void applyDetails(Event event, EventRequest request) {
        boolean existing = event.getId() != null;
        String category = categoryCode(request.category());
        if (category == null) {
            category = existing ? event.getCategory() : "SPORTS";
        }
        if (!category.equals(event.getCategory()) && !categories.lockActive(category).orElse(false)) {
            throw new InvalidRequestException("Choose an active event category");
        }
        String home = request.homeTeam();
        String away = request.awayTeam();
        String title = request.title();
        if (home != null || away != null) {
            if (!"SPORTS".equals(category)) {
                throw new InvalidRequestException("Team names are only supported in SPORTS");
            }
            validateText(home, "homeTeam");
            validateText(away, "awayTeam");
            home = home.strip();
            away = away.strip();
            if (title == null || title.isBlank()) {
                title = home + " vs " + away;
            }
        }
        String venue = request.venue() == null ? request.stadium() : request.venue();
        if (title == null || title.isBlank() || title.strip().length() > 514) {
            throw new InvalidRequestException("title must contain between 1 and 514 characters");
        }
        validateText(venue, "venue");
        validateText(request.organizer(), "organizer");
        if (request.startTime() == null) {
            throw new InvalidRequestException("startTime is required");
        }
        boolean saleOpen = request.saleOpen() == null ? !existing || event.isSaleOpen() : request.saleOpen();
        event.updateDetails(title.strip(), category, venue.strip(), home, away, request.startTime(),
            request.description(), request.organizer().strip(), saleOpen);
    }

    private String categoryCode(String category) {
        return category == null || category.isBlank() ? null : category.strip().toUpperCase(Locale.ROOT);
    }

    private TicketType addValidatedTicket(Event match, TicketTypeRequest request) {
        validateTicket(request);
        return match.addTicketType(request.name().trim(), request.price().setScale(2, RoundingMode.UNNECESSARY),
            request.totalQuantity());
    }

    @Override
    @Transactional
    public TicketType updateTicketType(Long matchId, Long ticketId, TicketTypeRequest request) {
        validateTicket(request);
        lockEvent(matchId);
        var ticket = ticketTypes.findForUpdate(ticketId)
            .filter(item -> item.matchId() == matchId)
            .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "Ticket type does not exist for this event"));
        if (request.totalQuantity() < ticket.allocatedQuantity()) {
            throw new BookingException(HttpStatus.CONFLICT, "Quantity cannot be lower than tickets already reserved");
        }
        ticket.updateDetails(request.name().trim(), request.price().setScale(2, RoundingMode.UNNECESSARY),
            request.totalQuantity());
        return ticket;
    }

    private void validateTicket(TicketTypeRequest request) {
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
    }

    private void validateText(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 255) {
            throw new InvalidRequestException(field + " must contain between 1 and 255 characters");
        }
    }
}
