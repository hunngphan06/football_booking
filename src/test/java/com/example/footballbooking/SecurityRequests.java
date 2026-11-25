package com.example.footballbooking;

import com.example.footballbooking.security.AccountPrincipal;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

final class SecurityRequests {
    private SecurityRequests() {}

    static RequestPostProcessor admin() {
        return user(new AccountPrincipal("admin@example.invalid", "encoded", "ADMIN", "Admin", null));
    }

    static RequestPostProcessor customer(long id) {
        return user(new AccountPrincipal("customer@example.invalid", "encoded", "USER", "Customer", id));
    }
}
