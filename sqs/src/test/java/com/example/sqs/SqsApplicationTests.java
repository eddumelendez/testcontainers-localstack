package com.example.sqs;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.cloud.aws.credentials.access-key=noop",
		"spring.cloud.aws.credentials.secret-key=noop", "spring.cloud.aws.region.static=us-east-1" })
@Testcontainers
class SqsApplicationTests {

	@Container
	static LocalStackContainer localStackContainer = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.0.0"));

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.aws.sqs.endpoint", () -> localStackContainer.getEndpoint().toString());
	}

	@Autowired
	private SqsTemplate sqsTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		this.sqsTemplate.send("test", "Hello World!");

		Awaitility.waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
		});
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

	}

	static class TestListener {

		private final List<String> messages = new ArrayList<>();

		@SqsListener("test")
		void listen(String message) {
			this.messages.add(message);
		}

	}

}
