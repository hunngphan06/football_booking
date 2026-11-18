package com.example.footballbooking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Sql("/match-fixtures.sql")
class FootballMatchApiTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void listEventsReturnsSampleMatchesWithTicketTypes() throws Exception {
        mvc.perform(get("/api/events"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].homeTeam").value("FC Sao Mai"))
            .andExpect(jsonPath("$[0].ticketTypes[0].price").value(200000.0));
    }

    @Test
    void getEventReturnsRequestedMatch() throws Exception {
        mvc.perform(get("/api/events/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.stadium").value("San Hoa Binh"));
    }

    @Test
    void missingEventReturnsStructured404() throws Exception {
        mvc.perform(get("/api/events/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Khong tim thay tran dau co id 999"))
            .andExpect(jsonPath("$.path").value("/api/events/999"));
    }

    @Test
    void nonNumericIdReturnsStructured400() throws Exception {
        mvc.perform(get("/api/events/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void oldFootballRouteAlsoReturnsList() throws Exception {
        mvc.perform(get("/football"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void filtersAndPaginatesEvents() throws Exception {
        mvc.perform(get("/api/events").param("keyword", "sao").param("venue", "anh duong")
                .param("from", "2026-12-05T00:00:00Z").param("to", "2026-12-06T00:00:00Z")
                .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].ticketTypes[0].availableQuantity").value(100))
            .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/events").param("page", "1").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void welcomeStillWorks() throws Exception {
        mvc.perform(get("/welcome"))
            .andExpect(status().isOk())
            .andExpect(content().string("Hệ thống đặt vé bóng đá đang hoạt động"));
    }

    @Test
    void createMatchWithTicketTypesThenReadItBack() throws Exception {
        String location = mvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"homeTeam":"Ha Noi","awayTeam":"Hai Phong","stadium":"Hang Day",
                     "startTime":"2026-12-08T12:00:00Z","organizer":"BTC",
                     "ticketTypes":[{"name":"VIP","price":500000,"totalQuantity":20}]}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
        assertNotNull(location);

        mvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Ha Noi vs Hai Phong"))
            .andExpect(jsonPath("$.ticketTypes[0].id").isNumber())
            .andExpect(jsonPath("$.ticketTypes[0].totalQuantity").value(20));
    }

    @Test
    void updateMatchKeepsExistingTicketsAndNewTicketPersists() throws Exception {
        mvc.perform(put("/api/events/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"homeTeam":"FC Sao Mai","awayTeam":"FC Song Xanh","stadium":"San Moi",
                     "startTime":"2026-12-08T12:00:00Z","organizer":"BTC"}
                    """))
            .andExpect(status().isOk());
        mvc.perform(post("/api/events/1/ticket-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"VIP","price":500000,"totalQuantity":20}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber());
        mvc.perform(get("/api/events/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stadium").value("San Moi"))
            .andExpect(jsonPath("$.ticketTypes.length()").value(2));
    }

    @Test
    void invalidTicketInNewMatchDoesNotLeavePartialData() throws Exception {
        mvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"homeTeam":"Ha Noi","awayTeam":"Hai Phong","stadium":"Hang Day",
                     "startTime":"2026-12-08T12:00:00Z","organizer":"BTC",
                     "ticketTypes":[{"name":"VIP","price":500000,"totalQuantity":0}]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("totalQuantity must be a positive integer"));
        mvc.perform(get("/api/events"))
            .andExpect(jsonPath("$.length()").value(2));
    }
}
