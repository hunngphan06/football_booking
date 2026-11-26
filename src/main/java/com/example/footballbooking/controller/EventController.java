package com.example.footballbooking.controller;

import com.example.footballbooking.dto.EventRequest;
import com.example.footballbooking.dto.TicketTypeRequest;
import com.example.footballbooking.model.Event;
import com.example.footballbooking.model.EventCover;
import com.example.footballbooking.exception.InvalidRequestException;
import com.example.footballbooking.model.TicketType;
import com.example.footballbooking.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RestController
public class EventController {
    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    // Giu /football cho bai tap cu; /api/events theo duong dan trong de bai.
    @GetMapping("/football")
    public List<Event> getEvents() {
        return service.list("SPORTS");
    }

    @GetMapping("/api/events")
    public Object getEvents(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String venue,
                            @RequestParam(required = false) Instant from,
                            @RequestParam(required = false) Instant to,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        if (keyword == null && venue == null && from == null && to == null && page == null && size == null) {
            return service.list(category);
        }
        return service.search(keyword, category, venue, from, to, page == null ? 0 : page, size == null ? 10 : size);
    }

    @GetMapping("/api/events/{id}")
    public Event getEvent(@PathVariable("id") Long id) {
        return service.getEvent(id);
    }

    @GetMapping("/api/events/{id}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable long id) {
        EventCover image = service.getCover(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .cacheControl(CacheControl.noStore()).body(image.data());
    }

    @PostMapping(value = "/api/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Event> createEvent(@RequestBody EventRequest request) {
        return created(service.createEvent(request, null));
    }

    @PostMapping(value = "/api/events", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Event> createEventWithCover(@RequestPart("event") EventRequest request,
                                                      @RequestPart("cover") MultipartFile upload) {
        return created(service.createEvent(request, readCover(upload)));
    }

    @PutMapping(value = "/api/events/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Event updateEvent(@PathVariable Long id, @RequestBody EventRequest request) {
        return service.updateEvent(id, request, null, false);
    }

    @PutMapping(value = "/api/events/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Event updateEventWithCover(@PathVariable Long id, @RequestPart("event") EventRequest request,
                                     @RequestPart(value = "cover", required = false) MultipartFile upload,
                                     @RequestParam(defaultValue = "false") boolean removeCover) {
        return service.updateEvent(id, request, upload == null ? null : readCover(upload), removeCover);
    }

    private ResponseEntity<Event> created(Event event) {
        return ResponseEntity.created(URI.create("/api/events/" + event.getId())).body(event);
    }

    private EventCover readCover(MultipartFile upload) {
        if (upload.getSize() > EventCover.MAX_BYTES) {
            throw new InvalidRequestException("Ảnh bìa tối đa 4 MB.");
        }
        try {
            return EventCover.decode(upload.getBytes());
        } catch (IOException ex) {
            throw new InvalidRequestException("Không đọc được tệp ảnh bìa.");
        }
    }

    @DeleteMapping("/api/events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        service.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/events/{id}/ticket-types")
    public ResponseEntity<TicketType> addTicketType(@PathVariable("id") Long id,
                                                   @RequestBody TicketTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTicketType(id, request));
    }

    @PutMapping("/api/events/{id}/ticket-types/{ticketId}")
    public TicketType updateTicketType(@PathVariable Long id, @PathVariable Long ticketId,
                                        @RequestBody TicketTypeRequest request) {
        return service.updateTicketType(id, ticketId, request);
    }
}
