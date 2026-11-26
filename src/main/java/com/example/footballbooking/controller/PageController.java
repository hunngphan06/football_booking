package com.example.footballbooking.controller;

import com.example.footballbooking.security.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping({"/events", "/events/"})
    public String events() {
        return "forward:/assets/events.html";
    }

    @GetMapping({"/events/{id:\\d+}", "/events/{id:\\d+}/"})
    public String eventDetails() {
        return "forward:/assets/event-detail.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/assets/login.html";
    }

    @GetMapping("/register")
    public String register() {
        return "forward:/assets/register.html";
    }

    @GetMapping("/my-bookings")
    public String bookings(@AuthenticationPrincipal AccountPrincipal account) {
        if (account == null || !"USER".equals(account.account().role())) {
            return "redirect:/login?next=/my-bookings";
        }
        return "forward:/assets/my-bookings.html";
    }

    @GetMapping({"/admin", "/admin/"})
    public String admin(@AuthenticationPrincipal AccountPrincipal account) {
        if (account == null) {
            return "redirect:/login";
        }
        return "ADMIN".equals(account.account().role()) ? "forward:/assets/admin.html" : "redirect:/events";
    }

    @GetMapping({"/", "/demo", "/demo/", "/demo/index.html"})
    public String home() {
        return "redirect:/events";
    }
}
