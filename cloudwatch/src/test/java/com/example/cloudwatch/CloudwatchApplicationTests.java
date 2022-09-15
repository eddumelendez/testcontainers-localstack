package com.example.cloudwatch;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.CLOUDWATCH;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.aws.credentials.access-key=noop", "spring.cloud.aws.credentials.secret-key=noop",
                "spring.cloud.aws.region.static=us-east-1", "management.metrics.export.cloudwatch.namespace=tc-localstack",
                "management.metrics.export.cloudwatch.step=5s",
                "management.metrics.enable.all=false", "management.metrics.enable.http=true"})
@AutoConfigureMetrics
@Testcontainers
class CloudwatchApplicationTests {

    @Container
    private static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.1.0")).withServices(LocalStackContainer.Service.CLOUDWATCH);

    @Autowired
    private CloudWatchAsyncClient cloudWatchAsyncClient;

    @LocalServerPort
    private int localPort;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.cloudwatch.endpoint",
                () -> localstack.getEndpointOverride(CLOUDWATCH).toString());
    }

    @Test
    void contextLoads() {
        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(Duration.ofSeconds(30));

        for (int i = 0; i < 5; i++) {
            RestAssured.given().port(this.localPort)
                    .get("/greetings")
                    .then()
                    .assertThat()
                    .body(equalTo("Hello World"));
        }

        Dimension exception = Dimension.builder().name("exception").value("None").build();
        Dimension method = Dimension.builder().name("method").value("GET").build();
        Dimension outcome = Dimension.builder().name("outcome").value("SUCCESS").build();
        Dimension uri = Dimension.builder().name("uri").value("/greetings").build();
        Dimension status = Dimension.builder().name("status").value("200").build();
        Metric metric = Metric.builder().namespace("tc-localstack").metricName("http.server.requests.count")
                .dimensions(exception, method, outcome, uri, status).build();
        MetricStat metricStat = MetricStat.builder().stat("Maximum").metric(metric).unit(StandardUnit.COUNT).period(5)
                .build();
        MetricDataQuery metricDataQuery = MetricDataQuery.builder().metricStat(metricStat).id("test1").returnData(true)
                .build();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(5)).ignoreExceptions().untilAsserted(() -> {
            GetMetricDataResponse response = this.cloudWatchAsyncClient.getMetricData(GetMetricDataRequest.builder()
                    .startTime(startTime).endTime(endTime).metricDataQueries(metricDataQuery).build()).get();
            assertThat(response.metricDataResults()).hasSize(1);
            assertThat(response.metricDataResults().get(0).label()).isEqualTo("http.server.requests.count Maximum");
            assertThat(response.metricDataResults().get(0).values()).contains(5d);
        });
    }

}
