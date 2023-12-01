package com.example.cognito;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class ExampleSecurity {

	@Bean
	protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.authorizeHttpRequests(c -> c.requestMatchers(HttpMethod.GET, "/topsecret")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/")
				.permitAll())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
			.build();
	}

}
