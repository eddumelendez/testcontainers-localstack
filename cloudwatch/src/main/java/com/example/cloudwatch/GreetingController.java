package com.example.cloudwatch;

import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Timed
@RestController
@RequestMapping("/greetings")
public class GreetingController {

	@GetMapping
	public String greetings() {
		return "Hello World";
	}

	@GetMapping("/{name}")
	public String greetings(@PathVariable String name) {
		return "Hello " + name;
	}

}
