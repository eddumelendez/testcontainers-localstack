package com.example.cognito;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExampleController {

	@GetMapping("/")
	public String unsecured() {
		return "No secrets here!\n";
	}

	@GetMapping("/topsecret")
	String secured() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
			Jwt jwt = (Jwt) authentication.getPrincipal();
			return String.format("You are [%s] with e-mail address [%s].%n", jwt.getSubject(),
					jwt.getClaimAsString("email"));
		}
		else {
			return "Something went wrong; authentication is not provided by Cognito.\n";
		}
	}

}
