package com.example.secretsmanager;

import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"spring.cloud.aws.credentials.access-key=noop", "spring.cloud.aws.credentials.secret-key=noop",
				"spring.cloud.aws.region.static=us-east-1", "spring.config.import=aws-secretsmanager:/spring/secret/text"})
@Testcontainers
class SecretsmanagerApplicationTests {

	@Container
	private static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.2.0"))
			.withServices(LocalStackContainer.Service.SECRETSMANAGER);

	@LocalServerPort
	private int localPort;

	private static final String text = "This is not a secret anymore. Love testing AWS with Testcontainers and LocalStack.";

	@BeforeAll
	static void beforeAll() throws IOException, InterruptedException {
		System.setProperty("spring.cloud.aws.secretsmanager.endpoint",
				localstack.getEndpointOverride(SECRETSMANAGER).toString());
		System.setProperty("spring.cloud.aws.secretsmanager.region", localstack.getRegion());

		localstack.execInContainer("awslocal", "secretsmanager", "create-secret", "--name", "/spring/secret/text", "--secret-string", text, "--region", localstack.getRegion());
	}

	@AfterAll
	static void afterAll() {
		System.clearProperty("spring.cloud.aws.secretsmanager.endpoint");
		System.clearProperty("spring.cloud.aws.secretsmanager.region");
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
