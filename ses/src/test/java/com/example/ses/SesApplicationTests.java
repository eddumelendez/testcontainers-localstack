package com.example.ses;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SesApplicationTests {

	@Container
	@ServiceConnection
	static LocalStackContainer localStackContainer = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.10.0"));

	@Autowired
	private MailSender mailSender;

	@BeforeAll
	static void beforeAll() throws IOException, InterruptedException {
		localStackContainer.execInContainer("awslocal", "ses", "verify-email-identity", "--email", "hello@example.com");
	}

	@Test
	void consumeMessage() {
		SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
		simpleMailMessage.setFrom("hello@example.com");
		simpleMailMessage.setTo("bar@baz.com");
		simpleMailMessage.setSubject("test subject");
		simpleMailMessage.setText("test content");
		this.mailSender.send(simpleMailMessage);
		var restClient = RestClient.builder().baseUrl(localStackContainer.getEndpoint().toString()).build();
		var response = restClient.get()
			.uri("/_aws/ses", uriBuilder -> uriBuilder.queryParam("email", "hello@example.com").build())
			.retrieve()
			.body(new ParameterizedTypeReference<Messages>() {
			});
		assertThat(response.messages()).hasSize(1);
		Message message = response.messages().getFirst();
		assertThat(message.source()).isEqualTo("hello@example.com");
		assertThat(message.subject()).isEqualTo("test subject");
		assertThat(message.body().textPart()).isEqualTo("test content");
	}

	record Messages(List<Message> messages) {

	}

	record Message(@JsonProperty("Id") String id, @JsonProperty("Region") String region,
			@JsonProperty("Destination") Destination destination, @JsonProperty("Source") String source,
			@JsonProperty("Subject") String subject, @JsonProperty("Body") Body body) {

	}

	record Destination(@JsonProperty("ToAddresses") List<String> toAddresses) {

	}

	record Body(@JsonProperty("text_part") String textPart, @JsonProperty("html_part") String htmlPart) {

	}

}
