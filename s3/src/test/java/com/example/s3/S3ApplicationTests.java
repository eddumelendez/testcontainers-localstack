package com.example.s3;

import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotification;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class S3ApplicationTests {

	@Container
	@ServiceConnection
	private static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.3.0"));

	@Autowired
	private S3Template s3Template;

	@Autowired
	private S3Client s3Client;

	@Autowired
	private TestListener testListener;

	private static final String POLICY = """
						{
			               "QueueConfigurations": [
			                   {
			                       "QueueArn": "%s",
			                       "Events": ["s3:ObjectCreated:*"]
			                   }
			               ]
			           }
			""";

	@BeforeAll
	static void beforeAll() throws IOException, InterruptedException {
		localstack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", "conferences", "--region",
				localstack.getRegion());
		try (var sqsClient = SqsClient.builder()
			.region(Region.of(localstack.getRegion()))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.endpointOverride(localstack.getEndpoint())
			.build()) {
			var queue = sqsClient.createQueue(builder -> builder.queueName("s3-event-notification-queue"));
			var queueAttributes = sqsClient.getQueueAttributes(
					builder -> builder.queueUrl(queue.queueUrl()).attributeNamesWithStrings("QueueArn"));
			var queueArn = queueAttributes.attributesAsStrings().get("QueueArn");
			localstack.execInContainer("awslocal", "s3api", "put-bucket-notification-configuration", "--bucket",
					"conferences", "--notification-configuration", String.format(POLICY, queueArn));
		}
	}

	@Test
	void contextLoads() {
		assertThat(this.s3Client.listBuckets().buckets()).hasSize(1);

		this.s3Template.store("conferences", "javaone.txt", "Las Vegas");
		ListObjectsV2Response listConferencesObjectsV2Response = this.s3Client
			.listObjectsV2(ListObjectsV2Request.builder().bucket("conferences").build());
		assertThat(listConferencesObjectsV2Response.contents()).hasSize(1);

		this.s3Template.createBucket("talks");
		assertThat(this.s3Client.listBuckets().buckets()).hasSize(2);

		this.s3Template.store("talks", "Adopting Testcontainers for local development.txt", "Oleg Šelajev");
		ListObjectsV2Response listTalksObjectsV2Response = this.s3Client
			.listObjectsV2(ListObjectsV2Request.builder().bucket("talks").build());
		assertThat(listTalksObjectsV2Response.contents()).hasSize(1);
		assertThat(testListener.messages).hasSize(2);
		var s3EventNotification = S3EventNotification.fromJson(testListener.messages.get(1));
		assertThat(s3EventNotification.getRecords().getFirst().getEventSource()).isEqualTo("aws:s3");
		assertThat(s3EventNotification.getRecords().getFirst().getEventName()).isEqualTo("ObjectCreated:Put");
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

		@SqsListener("s3-event-notification-queue")
		void listen(String message) {
			this.messages.add(message);
		}

	}

}
