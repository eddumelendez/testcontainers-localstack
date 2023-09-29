package com.example.springcloudfunctionaws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.function.Function;

@SpringBootApplication
public class SpringCloudFunctionAwsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudFunctionAwsApplication.class, args);
	}

	@Bean
	public Function<Profile, String> fetchByName(JdbcTemplate jdbcTemplate) {
		return profile -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profile WHERE name LIKE ?", String.class,
				profile.name() + "%");
	}

	record Profile(String name) {

	}

}
