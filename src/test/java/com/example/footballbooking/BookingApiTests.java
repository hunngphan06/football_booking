package com.example.footballbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:booking_flow_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BookingApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void pendingBookingCanBePaidAndRepeatedCancellationReturnsStockOnlyOnce() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        long customerId = createCustomer(email);
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                .content(customerBody(email.toUpperCase(java.util.Locale.ROOT))))
            .andExpect(status().isConflict());
        JsonNode match = createMatch();
        long eventId = match.get("id").asLong();
        long ticketId = match.at("/ticketTypes/0/id").asLong();
        long bookingId = json.readTree(mvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(customerId, eventId, ticketId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalAmount").value(200000.0))
            .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/events/{id}", eventId))
            .andExpect(jsonPath("$.ticketTypes[0].availableQuantity").value(0));
        mvc.perform(post("/api/bookings/{id}/pay", bookingId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/customers/{id}/bookings", customerId))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].items[0].quantity").value(1));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/bookings/{id}/cancel", bookingId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        }
        mvc.perform(post("/api/bookings/{id}/pay", bookingId)).andExpect(status().isConflict());
        mvc.perform(get("/api/events/{id}", eventId))
            .andExpect(jsonPath("$.ticketTypes[0].availableQuantity").value(1));
    }

    @Test
    void conflictingSecondTicketRollsBackFirstReservation() throws Exception {
        long customerId = createCustomer(UUID.randomUUID() + "@example.com");
        JsonNode match = createMatch();
        long eventId = match.get("id").asLong();
        long firstTicket = match.at("/ticketTypes/0/id").asLong();
        long secondTicket = match.at("/ticketTypes/1/id").asLong();
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("""
            {"customerId":%d,"eventId":%d,"items":[{"ticketTypeId":%d,"quantity":1},
                 {"ticketTypeId":%d,"quantity":2}]}
            """.formatted(customerId, eventId, firstTicket, secondTicket)))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/events/{id}", eventId))
            .andExpect(jsonPath("$.ticketTypes[0].availableQuantity").value(1))
            .andExpect(jsonPath("$.ticketTypes[1].availableQuantity").value(1));
        mvc.perform(get("/api/customers/{id}/bookings", customerId))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyOneOfTwoConcurrentRequestsCanReserveTheLastTicket() throws Exception {
        long buyer1 = createCustomer(UUID.randomUUID() + "@example.com");
        long buyer2 = createCustomer(UUID.randomUUID() + "@example.com");
        JsonNode match = createMatch();
        long matchId = match.get("id").asLong();
        long ticketId = match.at("/ticketTypes/0/id").asLong();
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var tasks = List.of(buyer1, buyer2).stream().map(buyer -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Requests did not start together");
                }
                return mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(buyer, matchId, ticketId)))
                    .andReturn().getResponse().getStatus();
            })).toList();
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Requests not ready");
            }
            start.countDown();
            int first = tasks.get(0).get(10, TimeUnit.SECONDS);
            int second = tasks.get(1).get(10, TimeUnit.SECONDS);
            assertEquals(List.of(201, 409), List.of(Math.min(first, second), Math.max(first, second)));
            mvc.perform(get("/api/events/{id}", matchId))
                .andExpect(jsonPath("$.ticketTypes[0].availableQuantity").value(0));
        } finally {
            pool.shutdownNow();
        }
    }

    private long createCustomer(String email) throws Exception {
        var response = mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                .content(customerBody(email)))
            .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("id").asLong();
    }

    private JsonNode createMatch() throws Exception {
        var response = mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
            {"homeTeam":"A","awayTeam":"B","stadium":"San A","startTime":"2026-12-08T12:00:00Z",
             "organizer":"BTC","ticketTypes":[{"name":"Standard","price":200000,"totalQuantity":1},
                                             {"name":"VIP","price":500000,"totalQuantity":1}]}
            """)).andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private String customerBody(String email) {
        return "{\"name\":\"Fan\",\"email\":\"" + email + "\"}";
    }

    private String bookingBody(long customerId, long eventId, long ticketId) {
        return """
            {"customerId":%d,"eventId":%d,"items":[{"ticketTypeId":%d,"quantity":1}]}
            """.formatted(customerId, eventId, ticketId);
    }
}
