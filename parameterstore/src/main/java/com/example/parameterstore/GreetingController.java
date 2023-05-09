package com.example.parameterstore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

	@Value("${text}")
	private String text;

	@GetMapping("/greetings")
	public String greeting() {
		return this.text;
	}

}
