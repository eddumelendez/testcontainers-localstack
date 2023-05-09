package com.example.s3;

import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"spring.cloud.aws.credentials.access-key=noop", "spring.cloud.aws.credentials.secret-key=noop",
				"spring.cloud.aws.region.static=us-east-1"})
@Testcontainers
class S3ApplicationTests {

	@Container
	private static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0.0"));

	@Autowired
	private S3Template s3Template;

	@Autowired
	private S3Client s3Client;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.aws.s3.endpoint",
				() -> localstack.getEndpointOverride(S3).toString());
		registry.add("spring.cloud.aws.s3.region", localstack::getRegion);
	}

	@BeforeAll
	static void beforeAll() throws IOException, InterruptedException {
		localstack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", "conferences", "--region", localstack.getRegion());
	}

	@Test
	void contextLoads() {
		assertThat(this.s3Client.listBuckets().buckets()).hasSize(1);

		this.s3Template.store("conferences", "javaone.txt", "Las Vegas");
		ListObjectsV2Response listConferencesObjectsV2Response = this.s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket("conferences").build());
		assertThat(listConferencesObjectsV2Response.contents()).hasSize(1);

		this.s3Template.createBucket("talks");
		assertThat(this.s3Client.listBuckets().buckets()).hasSize(2);

		this.s3Template.store("talks", "Adopting Testcontainers for local development.txt", "Oleg Šelajev");
		ListObjectsV2Response listTalksObjectsV2Response = this.s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket("talks").build());
		assertThat(listTalksObjectsV2Response.contents()).hasSize(1);
	}

}
