package com.example.footballbooking.security;

import com.example.footballbooking.dto.AccountData.Registration;
import com.example.footballbooking.repository.AccountRepository;
import com.example.footballbooking.service.AccountService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain bookingSecurity(HttpSecurity http) throws Exception {
        return http
            .requestCache(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/assets/*.html").denyAll()
                .requestMatchers(HttpMethod.GET, "/", "/admin", "/admin/", "/events", "/events/", "/events/*",
                    "/events/*/", "/login", "/register", "/my-bookings", "/welcome", "/football",
                    "/assets/**", "/demo", "/demo/", "/demo/index.html", "/api/auth/csrf", "/api/auth/me",
                    "/api/events", "/api/events/*", "/api/events/*/cover", "/api/event-categories", "/swagger-ui.html",
                    "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/customers",
                    "/api/assistant/search", "/api/events/*/ask").permitAll()
                .requestMatchers("/api/events", "/api/events/**", "/api/event-categories", "/api/event-categories/**")
                    .hasRole("ADMIN")
                .requestMatchers("/api/bookings", "/api/bookings/**", "/api/me/bookings", "/api/customers/*/bookings").hasRole("USER")
                .anyRequest().denyAll())
            .formLogin(login -> login.loginPage("/login").loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, exception) -> error(response, 401, "Email hoặc mật khẩu không đúng.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> error(response, 401, "Bạn cần đăng nhập."))
                .accessDeniedHandler((request, response, exception) -> error(response, 403, "Bạn không có quyền hoặc phiên đã hết hạn.")))
            .build();
    }

    @Bean
    UserDetailsService bookingUsers(AccountRepository accounts) {
        return email -> accounts.find(email.strip().toLowerCase(Locale.ROOT))
            .map(account -> new AccountPrincipal(account.email(), account.passwordHash(), account.role(),
                account.name(), account.customerId()))
            .orElseThrow(() -> new UsernameNotFoundException("Account does not exist"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    ApplicationRunner initializeAccounts(AccountService service, AccountRepository accounts,
        Environment environment,
        @Value("${booking.admin.email:}") String adminEmail,
        @Value("${booking.admin.password:}") String adminPassword) {
        return args -> {
            if (environment.acceptsProfiles(Profiles.of("demo"))) {
                service.createAdmin("admin@demo.example", "AdminDemo2026!");
                if (accounts.find("khach@demo.example").isEmpty()) {
                    service.register(new Registration("Khách demo", "khach@demo.example", "KhachDemo2026!"));
                }
            } else if (!adminEmail.isBlank() || !adminPassword.isBlank()) {
                if (adminEmail.isBlank() || adminPassword.isBlank()) {
                    throw new IllegalStateException("Provide both BOOKING_ADMIN_EMAIL and BOOKING_ADMIN_PASSWORD");
                }
                service.createAdmin(adminEmail, adminPassword);
            }
        };
    }

    private static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
