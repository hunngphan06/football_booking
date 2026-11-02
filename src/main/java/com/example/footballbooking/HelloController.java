package com.example.footballbooking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
public class HelloController {
    @GetMapping ("/welcome")
    public String hello(){
        return "Hệ thống đặt vé bóng đá đang hoạt động";
    }    
}
