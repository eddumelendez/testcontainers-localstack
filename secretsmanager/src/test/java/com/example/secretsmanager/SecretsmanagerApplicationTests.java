package com.example.secretsmanager;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SSM;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"spring.cloud.aws.credentials.access-key=noop", "spring.cloud.aws.credentials.secret-key=noop",
				"spring.cloud.aws.region.static=us-east-1"})
@Testcontainers
class SecretsmanagerApplicationTests {

	@Container
	private static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.1.0"))
			.withServices(LocalStackContainer.Service.SECRETSMANAGER);

	@LocalServerPort
	private int localPort;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.aws.secretsmanager.endpoint",
				() -> localstack.getEndpointOverride(SECRETSMANAGER).toString());
		registry.add("spring.cloud.aws.secretsmanager.region", localstack::getRegion);
	}

	private static String text = "This is not a secret anymore. Love testing AWS with Testcontainers and LocalStack.";

	@BeforeAll
	static void beforeAll() throws IOException, InterruptedException {
		localstack.execInContainer("awslocal", "secretsmanager", "create-secret", "--name", "/spring/secret/text", "--secret-string", text, "--region", localstack.getRegion());
	}

	@Test
	void contextLoads() {
		RestAssured.given().port(this.localPort)
				.get("/greetings")
				.then()
				.assertThat()
				.body(equalTo(text));
	}

}
