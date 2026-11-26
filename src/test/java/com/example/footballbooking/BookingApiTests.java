package com.example.footballbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.example.footballbooking.repository.BookingRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:booking_flow_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "booking.demo.enabled=true",
    "booking.demo.payment-scenarios-enabled=true"
})
@AutoConfigureMockMvc
class BookingApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private BookingRepository bookings;

    @Test
    void confirmedBookingDownloadsReadableVietnamesePdfAndCannotReduceReservedStock() throws Exception {
        long customer = createCustomer(UUID.randomUUID() + "@example.com");
        JsonNode match = createMatch();
        long event = match.get("id").asLong();
        long ticket = match.at("/ticketTypes/0/id").asLong();
        mvc.perform(put("/api/events/{id}", event).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"homeTeam":"Hà Nội","awayTeam":"Hải Phòng","stadium":"Hàng Đẫy",
                 "startTime":"2026-12-08T12:00:00Z","organizer":"BTC"}
                """)).andExpect(status().isOk());
        long booking = json.readTree(mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
            .content(bookingBody(customer, event, ticket))).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/bookings/{id}/confirmation", booking).accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isConflict()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
        mvc.perform(put("/api/events/{id}/ticket-types/{ticketId}", event, ticket)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"VIP\",\"price\":500000,\"totalQuantity\":2}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.availableQuantity").value(1));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
            .content(bookingBody(customer, event, ticket))).andExpect(status().isCreated());
        mvc.perform(put("/api/events/{id}/ticket-types/{ticketId}", event, ticket)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"VIP\",\"price\":500000,\"totalQuantity\":1}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings/{id}/pay", booking)).andExpect(status().isOk());
        byte[] bytes = mvc.perform(get("/api/bookings/{id}/confirmation", booking))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", "attachment; filename=booking-" + booking + ".pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        try (var pdf = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            org.junit.jupiter.api.Assertions.assertTrue(text.contains("Hà Nội vs Hải Phòng"));
            org.junit.jupiter.api.Assertions.assertTrue(text.contains("200000.00 VND"));
            org.junit.jupiter.api.Assertions.assertTrue(text.contains("19:00"));
        }
        mvc.perform(post("/api/bookings/{id}/cancel", booking)).andExpect(status().isOk());
        mvc.perform(get("/api/bookings/{id}/confirmation", booking)).andExpect(status().isConflict());
        mvc.perform(get("/api/bookings/9223372036854775807/confirmation")).andExpect(status().isNotFound());
    }

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

    @Test
    void failureBeforeChargeKeepsBookingPendingAndAllowsRetry() throws Exception {
        long id = newBooking();
        mvc.perform(post("/api/bookings/{id}/refund", id)).andExpect(status().isNotFound());
        mvc.perform(paymentScenario(id, "FAIL_BEFORE_CHARGE")).andExpect(status().isConflict());
        mvc.perform(get("/api/bookings/{id}", id)).andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.paymentStatus").value("FAILED"));
        mvc.perform(get("/api/bookings/{id}/payment", id)).andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.chargedAt").isEmpty()).andExpect(jsonPath("$.refundedAt").isEmpty());
        mvc.perform(post("/api/bookings/{id}/refund", id)).andExpect(status().isConflict());
        mvc.perform(post("/api/bookings/{id}/pay", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.paymentStatus").value("SUCCEEDED"));
    }

    @Test
    void timeoutRequiresReconciliationAndKeepsOneChargeReference() throws Exception {
        long id = newBooking();
        mvc.perform(paymentScenario(id, "TIMEOUT")).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        mvc.perform(post("/api/bookings/{id}/cancel", id)).andExpect(status().isConflict());
        mvc.perform(post("/api/bookings/{id}/pay", id)).andExpect(status().isAccepted());
        mvc.perform(get("/api/bookings/{id}/payment", id)).andExpect(jsonPath("$.chargedAt").isEmpty());
        mvc.perform(post("/api/bookings/{id}/payment/reconcile", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
        JsonNode firstCharge = paymentRow(id);
        mvc.perform(post("/api/bookings/{id}/payment/reconcile", id)).andExpect(status().isOk());
        mvc.perform(post("/api/bookings/{id}/pay", id)).andExpect(status().isOk());
        assertEquals(firstCharge, paymentRow(id));
    }

    @Test
    void failedConfirmationRollsBackPaidAtAndRefundIsConfirmedOnce() throws Exception {
        long id = newBooking();
        mvc.perform(paymentScenario(id, "FAIL_AFTER_CHARGE")).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.paidAt").isEmpty())
            .andExpect(jsonPath("$.paymentStatus").value("REFUND_PENDING"));
        mvc.perform(get("/api/bookings/{id}/payment", id)).andExpect(jsonPath("$.chargedAt").isNotEmpty())
            .andExpect(jsonPath("$.refundedAt").isEmpty()).andExpect(jsonPath("$.amount").value(200000));
        mvc.perform(post("/api/bookings/{id}/refund", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED")).andExpect(jsonPath("$.refundedAt").isNotEmpty());
        JsonNode refund = paymentRow(id);
        mvc.perform(post("/api/bookings/{id}/refund", id)).andExpect(status().isOk());
        mvc.perform(post("/api/bookings/{id}/cancel", id)).andExpect(status().isOk());
        assertEquals(refund, paymentRow(id));
        var order = body(mvc.perform(get("/api/bookings/{id}", id)).andExpect(status().isOk()));
        mvc.perform(get("/api/events/{id}", order.get("eventId").asLong()))
            .andExpect(jsonPath("$.ticketTypes[0].availableQuantity").value(1));
    }

    @Test
    void categoryCanBeManagedAndBookedEventCanBeClosedWithoutDeletingHistory() throws Exception {
        String code = "TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        String categoryUrl = "/api/event-categories/" + code;
        mvc.perform(post("/api/event-categories").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"%s\",\"name\":\"Suất chiếu thử\"}".formatted(code)))
            .andExpect(status().isCreated());
        var event = body(mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventBody(code, true, true))).andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Chuyến tàu")).andExpect(jsonPath("$.venue").value("Rạp A")));
        long id = event.get("id").asLong();
        long ticket = event.at("/ticketTypes/0/id").asLong();
        long customer = createCustomer(UUID.randomUUID() + "@example.com");
        var order = body(mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
            .content(bookingBody(customer, id, ticket))).andExpect(status().isCreated()));
        mvc.perform(get("/api/events").param("category", code.toLowerCase(java.util.Locale.ROOT)).param("keyword", "Chuyến"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(delete("/api/events/{id}", id)).andExpect(status().isConflict());
        mvc.perform(delete(categoryUrl)).andExpect(status().isConflict());
        mvc.perform(put(categoryUrl).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Suất chiếu\",\"active\":false}")).andExpect(status().isOk());
        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventBody(code, true, true))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/events/{id}", id).contentType(MediaType.APPLICATION_JSON)
            .content(eventBody(code, false, false))).andExpect(status().isOk())
            .andExpect(jsonPath("$.saleOpen").value(false));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
            .content(bookingBody(customer, id, ticket))).andExpect(status().isConflict());
        mvc.perform(post("/api/bookings/{id}/pay", order.get("id").asLong())).andExpect(status().isOk());
    }

    @Test
    void unusedEventAndCategoryCanBeDeleted() throws Exception {
        String code = "TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        mvc.perform(post("/api/event-categories").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"%s\",\"name\":\"Nhạc thử\"}".formatted(code)))
            .andExpect(status().isCreated());
        var event = body(mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventBody(code, true, true))).andExpect(status().isCreated()));
        mvc.perform(delete("/api/events/{id}", event.get("id").asLong())).andExpect(status().isNoContent());
        mvc.perform(get("/api/events/{id}", event.get("id").asLong())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/event-categories/{code}", code)).andExpect(status().isNoContent());
    }

    private String eventBody(String code, boolean saleOpen, boolean includeTickets) {
        String fields = """
            "title":"Chuyến tàu", "venue":"Rạp A", "category":"%s", "saleOpen":%s,
            "startTime":"2026-12-08T12:00:00Z", "organizer":"Rạp A"
            """.formatted(code, saleOpen).strip();
        if (includeTickets) {
            fields += """
                , "ticketTypes":[{"name":"Ghế thường","price":80000,"totalQuantity":2}]
                """;
        }
        return "{" + fields + "}";
    }

    private long newBooking() throws Exception {
        long customer = createCustomer(UUID.randomUUID() + "@example.com");
        JsonNode match = createMatch();
        return body(mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
            .content(bookingBody(customer, match.get("id").asLong(), match.at("/ticketTypes/0/id").asLong())))
            .andExpect(status().isCreated())).get("id").asLong();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder paymentScenario(long id, String scenario) {
        return post("/api/bookings/{id}/pay", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"scenario\":\"%s\"}".formatted(scenario));
    }

    private JsonNode paymentRow(long id) throws Exception {
        return body(mvc.perform(get("/api/bookings/{id}/payment", id)).andExpect(status().isOk()));
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
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

    @Test
    void adminCanCreateReplaceAndRemoveEventCover() throws Exception {
        var created = mvc.perform(MockMvcRequestBuilders.multipart("/api/events")
            .file(eventPart("Suất có ảnh")).file(coverPart("png", Color.RED)).with(csrf()).with(SecurityRequests.admin()))
            .andExpect(status().isCreated()).andReturn().getResponse();
        JsonNode event = json.readTree(created.getContentAsByteArray());
        long id = event.get("id").asLong();
        String firstUrl = event.get("coverUrl").asText();
        assertCover(firstUrl, MediaType.IMAGE_PNG, Color.RED);

        mvc.perform(put("/api/events/{id}", id).contentType(MediaType.APPLICATION_JSON)
            .content(eventBodyForCover("Giữ ảnh"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.coverUrl").value(firstUrl));

        var updated = mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/events/{id}", id)
            .file(eventPart("Thay ảnh")).file(coverPart("jpeg", Color.BLUE)).with(csrf()).with(SecurityRequests.admin()))
            .andExpect(status().isOk()).andReturn().getResponse();
        String secondUrl = json.readTree(updated.getContentAsByteArray()).get("coverUrl").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals(firstUrl, secondUrl);
        assertCover(secondUrl, MediaType.IMAGE_JPEG, Color.BLUE);

        mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/events/{id}", id)
            .file(eventPart("Gỡ ảnh")).param("removeCover", "true").with(csrf()).with(SecurityRequests.admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.coverUrl").isEmpty());
        mvc.perform(MockMvcRequestBuilders.get(secondUrl)).andExpect(status().isNotFound());
    }

    @Test
    void invalidOrUnauthorizedCoverDoesNotChangeTheEvent() throws Exception {
        JsonNode event = createMatch();
        long id = event.get("id").asLong();
        var invalid = new MockMultipartFile("cover", "fake.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/events/{id}", id)
            .file(eventPart("Tiêu đề không được lưu")).file(invalid).with(csrf()).with(SecurityRequests.admin()))
            .andExpect(status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/events/{id}", id)
            .file(eventPart("Tiêu đề không được lưu")).file(coverPart("png", Color.RED)).with(csrf())
            .with(SecurityRequests.customer(1)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/events/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value(event.get("title").asText()))
            .andExpect(jsonPath("$.coverUrl").isEmpty());
    }

    private MockMultipartFile eventPart(String title) {
        return new MockMultipartFile("event", "", "application/json",
            eventBodyForCover(title).getBytes(StandardCharsets.UTF_8));
    }

    private String eventBodyForCover(String title) {
        return """
            {"title":"%s","category":"CINEMA","venue":"Rạp A","organizer":"BTC",
             "startTime":"2026-12-08T12:00:00Z"}
            """.formatted(title);
    }

    private MockMultipartFile coverPart(String format, Color color) throws Exception {
        var picture = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        var graphics = picture.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, picture.getWidth(), picture.getHeight());
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(picture, format, bytes);
        return new MockMultipartFile("cover", "cover." + format, "image/" + format, bytes.toByteArray());
    }

    private void assertCover(String url, MediaType contentType, Color color) throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.get(url)).andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn().getResponse();
        var picture = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
        var actual = new Color(picture.getRGB(0, 0));
        org.junit.jupiter.api.Assertions.assertTrue(Math.abs(actual.getRed() - color.getRed()) < 5
            && Math.abs(actual.getGreen() - color.getGreen()) < 5
            && Math.abs(actual.getBlue() - color.getBlue()) < 5);
    }

    private MockHttpServletRequestBuilder post(String path, Object... values) {
        return authorize(MockMvcRequestBuilders.post(path, values).with(csrf()), path, values);
    }

    private MockHttpServletRequestBuilder put(String path, Object... values) {
        return authorize(MockMvcRequestBuilders.put(path, values).with(csrf()), path, values);
    }

    private MockHttpServletRequestBuilder delete(String path, Object... values) {
        return authorize(MockMvcRequestBuilders.delete(path, values).with(csrf()), path, values);
    }

    private MockHttpServletRequestBuilder get(String path, Object... values) {
        return authorize(MockMvcRequestBuilders.get(path, values), path, values);
    }

    private MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder request, String path, Object[] values) {
        if (path.equals("/api/bookings")) {
            return request.with(raw -> {
                try {
                    long owner = json.readTree(raw.getContentAsByteArray()).get("customerId").asLong();
                    return SecurityRequests.customer(owner).postProcessRequest(raw);
                } catch (java.io.IOException ex) {
                    throw new IllegalArgumentException(ex);
                }
            });
        }
        if (path.startsWith("/api/bookings/")) {
            long owner = values.length == 0 ? 1 : bookings.findBooking(((Number) values[0]).longValue())
                .map(booking -> booking.customerId()).orElse(1L);
            return request.with(SecurityRequests.customer(owner));
        }
        if (path.equals("/api/customers/{id}/bookings")) {
            return request.with(SecurityRequests.customer(((Number) values[0]).longValue()));
        }
        return request.with(SecurityRequests.admin());
    }

    private String customerBody(String email) {
        return "{\"name\":\"Fan\",\"email\":\"" + email + "\",\"password\":\"TestPassword2026!\"}";
    }

    private String bookingBody(long customerId, long eventId, long ticketId) {
        return """
            {"customerId":%d,"eventId":%d,"items":[{"ticketTypeId":%d,"quantity":1}]}
            """.formatted(customerId, eventId, ticketId);
    }
}
