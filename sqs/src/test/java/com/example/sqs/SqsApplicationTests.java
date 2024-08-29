package com.example.sqs;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ExtendWith(SqsApplicationTests.SqsAfterAllCallBack.class)
class SqsApplicationTests {

	@Container
	@ServiceConnection
	static LocalStackContainer localStackContainer = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.7.0"));

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

	static class SqsAfterAllCallBack implements AfterAllCallback {

		@Override
		public void afterAll(ExtensionContext context) throws Exception {
			ConfigurableApplicationContext applicationContext = (ConfigurableApplicationContext) SpringExtension
				.getApplicationContext(context);
			applicationContext.stop();
		}

	}

}
