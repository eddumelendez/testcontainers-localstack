package com.example.parameterstore;

import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SSM;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.aws.credentials.access-key=noop", "spring.cloud.aws.credentials.secret-key=noop",
                "spring.cloud.aws.region.static=us-east-1", "spring.config.import=aws-parameterstore:/spring/config/"})
class ParameterstoreApplicationTests {

    private static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.1.0"))
            .withServices(LocalStackContainer.Service.SSM);

    @LocalServerPort
    private int localPort;

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        localstack.start();
        System.setProperty("spring.cloud.aws.parameterstore.endpoint",
                localstack.getEndpointOverride(SSM).toString());
        System.setProperty("spring.cloud.aws.parameterstore.region", localstack.getRegion());

        localstack.execInContainer("awslocal", "ssm", "put-parameter", "--name", "/spring/config/text", "--value", "Hello World", "--type", "String", "--region", localstack.getRegion());
    }

    @AfterAll
    static void afterAll() {
        if (localstack != null) {
            localstack.stop();
        }
        System.clearProperty("spring.cloud.aws.parameterstore.endpoint");
        System.clearProperty("spring.cloud.aws.parameterstore.region");
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
