package com.example.secretsmanager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    @Value("${text}")
    private String secret;

    @GetMapping("/greetings")
    public String greeting() {
        return this.secret;
    }
}
