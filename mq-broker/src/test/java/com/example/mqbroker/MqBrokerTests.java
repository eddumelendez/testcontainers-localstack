package com.example.mqbroker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mq.MqClient;
import software.amazon.awssdk.services.mq.model.BrokerState;
import software.amazon.awssdk.services.mq.model.CreateBrokerRequest;
import software.amazon.awssdk.services.mq.model.CreateBrokerResponse;
import software.amazon.awssdk.services.mq.model.DescribeBrokerRequest;
import software.amazon.awssdk.services.mq.model.EngineType;
import software.amazon.awssdk.services.mq.model.User;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.time.Duration;

import org.apache.activemq.ActiveMQConnectionFactory;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "LOCALSTACK_AUTH_TOKEN", matches = ".+")
public class MqBrokerTests {

	@Container
	private static final LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack-pro:4.9.1"))
		.withExposedPorts(4566, 4510, 4511)
		.withEnv("LOCALSTACK_AUTH_TOKEN", System.getenv("LOCALSTACK_AUTH_TOKEN"));

	@Test
	void test() throws Exception {
		AwsBasicCredentials awsCreds = AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey());

		try (MqClient mqClient = MqClient.builder()
			.region(Region.of(localstack.getRegion()))
			.endpointOverride(localstack.getEndpoint())
			.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
			.build()) {

			User adminUser = User.builder().username("admin").password("admin123456789").build();

			CreateBrokerRequest createBrokerRequest = CreateBrokerRequest.builder()
				.brokerName("test-broker")
				.engineType(EngineType.ACTIVEMQ)
				.engineVersion("5.18")
				.hostInstanceType("mq.t3.micro")
				.publiclyAccessible(true)
				.users(adminUser)
				.overrideConfiguration(
						AwsRequestOverrideConfiguration.builder().apiCallAttemptTimeout(Duration.ofMinutes(3)).build())
				.build();

			CreateBrokerResponse createBrokerResponse = mqClient.createBroker(createBrokerRequest);
			String brokerId = createBrokerResponse.brokerId();

			DescribeBrokerRequest describeBrokerRequest = DescribeBrokerRequest.builder().brokerId(brokerId).build();

			await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
				BrokerState brokerState = mqClient.describeBroker(describeBrokerRequest).brokerState();
				System.out.println("Broker state: " + brokerState);
				assertThat(brokerState).isEqualTo(BrokerState.RUNNING);
			});

			String messageContent = "Hello, ActiveMQ!";
			given().baseUri("http://%s:%d".formatted(localstack.getHost(), localstack.getMappedPort(4510)))
				.auth()
				.preemptive()
				.basic("admin", "admin")
				.queryParam("destination", "queue://test.topic")
				.body(messageContent)
				.post("/api/message")
				.then()
				.statusCode(200);

			String brokerEndpoint = "tcp://%s:%d".formatted(localstack.getHost(), localstack.getMappedPort(4511));

			ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("admin", "admin123456789",
					brokerEndpoint);

			try (Connection connection = connectionFactory.createConnection()) {
				connection.start();
				Session session = connection.createSession();
				Queue queue = session.createQueue("test.topic");
				MessageConsumer consumer = session.createConsumer(queue);

				Message message = consumer.receive(5000);
				String receivedContent = ((TextMessage) message).getText();
				assertThat(receivedContent).isEqualToIgnoringNewLines(messageContent);

				consumer.close();
				session.close();
			}
		}
	}

}
