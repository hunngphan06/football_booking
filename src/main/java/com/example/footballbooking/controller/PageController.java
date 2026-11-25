package com.example.footballbooking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping({"/events", "/events/"})
    public String events() {
        return "forward:/assets/events.html";
    }

    @GetMapping({"/admin", "/admin/"})
    public String admin() {
        return "forward:/assets/admin.html";
    }

    @GetMapping({"/", "/demo", "/demo/", "/demo/index.html"})
    public String home() {
        return "redirect:/events";
    }
}
