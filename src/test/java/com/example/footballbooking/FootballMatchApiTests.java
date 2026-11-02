package com.example.footballbooking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
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
            .andExpect(jsonPath("$[0].ticketTypes[0].price").value(200000));
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
    void welcomeStillWorks() throws Exception {
        mvc.perform(get("/welcome"))
            .andExpect(status().isOk())
            .andExpect(content().string("Hệ thống đặt vé bóng đá đang hoạt động"));
    }
}
