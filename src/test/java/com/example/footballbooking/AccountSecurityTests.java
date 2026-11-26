package com.example.footballbooking;

import com.example.footballbooking.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:account_flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "booking.demo.enabled=true",
    "booking.admin.email=admin@account.example",
    "booking.admin.password=TestPassword2026!"
})
@AutoConfigureMockMvc
class AccountSecurityTests {
    private static final String PASSWORD = "TestPassword2026!";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AccountRepository accounts;

    @Test
    void adminManagesEventsAndOnlyTheCustomerCanActOnTheirBooking() throws Exception {
        mvc.perform(get("/demo/index.html")).andExpect(redirectedUrl("/events"));
        mvc.perform(get("/demo")).andExpect(redirectedUrl("/events"));
        mvc.perform(get("/admin")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/my-bookings")).andExpect(redirectedUrl("/login?next=/my-bookings"));
        mvc.perform(get("/events")).andExpect(forwardedUrl("/assets/events.html"));
        mvc.perform(get("/events/42")).andExpect(forwardedUrl("/assets/event-detail.html"));
        mvc.perform(get("/login")).andExpect(forwardedUrl("/assets/login.html"));
        mvc.perform(get("/register")).andExpect(forwardedUrl("/assets/register.html"));
        mvc.perform(get("/assets/admin.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/events").with(csrf())).andExpect(status().isUnauthorized());

        MockHttpSession admin = signIn("admin@account.example", PASSWORD);
        mvc.perform(get("/admin").session(admin)).andExpect(forwardedUrl("/assets/admin.html"));
        mvc.perform(get("/my-bookings").session(admin)).andExpect(redirectedUrl("/login?next=/my-bookings"));
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(jsonPath("$.account.role").value("ADMIN"));
        mvc.perform(post("/api/events").session(admin)).andExpect(status().isForbidden());
        var created = mvc.perform(post("/api/events").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Suất test","category":"CINEMA","venue":"Rạp A","organizer":"Rạp A",
                 "startTime":"2026-12-01T12:00:00Z","ticketTypes":[{"name":"Thường","price":85000,"totalQuantity":5}]}
                """)).andExpect(status().isCreated()).andReturn();
        var event = json.readTree(created.getResponse().getContentAsByteArray());
        long eventId = event.get("id").asLong();
        long ticketId = event.at("/ticketTypes/0/id").asLong();
        mvc.perform(get("/api/me/bookings").session(admin)).andExpect(status().isForbidden());

        long ownerId = register("owner@account.example");
        long otherId = register("other@account.example");
        var stored = accounts.find("owner@account.example").orElseThrow();
        assertNotEquals(PASSWORD, stored.passwordHash());
        assertTrue(stored.passwordHash().startsWith("{bcrypt}"));
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "owner@account.example")
            .param("password", "incorrect")).andExpect(status().isUnauthorized());

        MockHttpSession owner = signIn("OWNER@account.example", PASSWORD);
        MockHttpSession other = signIn("other@account.example", PASSWORD);
        mvc.perform(get("/my-bookings").session(owner)).andExpect(forwardedUrl("/assets/my-bookings.html"));
        mvc.perform(get("/admin").session(owner)).andExpect(redirectedUrl("/events"));
        mvc.perform(get("/api/auth/me").session(owner)).andExpect(jsonPath("$.account.role").value("USER"))
            .andExpect(jsonPath("$.account.customerId").value(ownerId))
            .andExpect(jsonPath("$.demo").value(true));
        mvc.perform(delete("/api/events/{id}", eventId).session(owner).with(csrf())).andExpect(status().isForbidden());

        var item = Map.of("ticketTypeId", ticketId, "quantity", 2);
        mvc.perform(post("/api/bookings").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("customerId", otherId, "eventId", eventId, "items", new Object[]{item}))))
            .andExpect(status().isForbidden());
        var order = mvc.perform(post("/api/bookings").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("eventId", eventId, "items", new Object[]{item}))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.customerId").value(ownerId)).andReturn();
        long orderId = json.readTree(order.getResponse().getContentAsByteArray()).get("id").asLong();
        mvc.perform(get("/api/me/bookings").session(owner)).andExpect(jsonPath("$[0].id").value(orderId));
        mvc.perform(get("/api/me/bookings").session(other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/customers/{id}/bookings", ownerId).session(other)).andExpect(status().isNotFound());
        for (String path : new String[] {"/api/bookings/{id}", "/api/bookings/{id}/confirmation", "/api/bookings/{id}/payment"}) {
            mvc.perform(get(path, orderId).session(other)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/bookings/{id}/pay", orderId).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/{id}/cancel", orderId).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/{id}/pay", orderId).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("scenario", "TIMEOUT")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings/{id}/pay", orderId).session(owner).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/bookings/{id}/confirmation", orderId).session(owner))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(post("/api/auth/logout").session(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/bookings")).andExpect(status().isUnauthorized());
    }

    private long register(String email) throws Exception {
        var response = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("name", "Khách", "email", email, "password", PASSWORD))))
            .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).get("id").asLong();
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).param("email", email).param("password", password))
            .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
