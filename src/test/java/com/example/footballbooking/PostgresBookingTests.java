package com.example.footballbooking;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Run with TEST_PG_URL, TEST_PG_USER, TEST_PG_PASSWORD pointing to a test database. */
@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
class PostgresBookingTests extends BookingApiTests {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TEST_PG_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("TEST_PG_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_PG_PASSWORD"));
    }
}
