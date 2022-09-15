package com.example.parameterstore;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SSM;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.aws.credentials.access-key=noop", "spring.cloud.aws.credentials.secret-key=noop",
                "spring.cloud.aws.region.static=us-east-1"})
@Testcontainers
class ParameterstoreApplicationTests {

    @Container
    private static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.1.0"))
            .withServices(LocalStackContainer.Service.SSM);

    @LocalServerPort
    private int localPort;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.parameterstore.endpoint",
                () -> localstack.getEndpointOverride(SSM).toString());
        registry.add("spring.cloud.aws.parameterstore.region", localstack::getRegion);
    }

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        localstack.execInContainer("awslocal", "ssm", "put-parameter", "--name", "/spring/config/text", "--value", "Hello World", "--type", "String", "--region", localstack.getRegion());
    }

    @Test
    void contextLoads() {
        RestAssured.given().port(this.localPort)
                .get("/greetings")
                .then()
                .assertThat()
                .body(equalTo("Hello World"));
    }

}
