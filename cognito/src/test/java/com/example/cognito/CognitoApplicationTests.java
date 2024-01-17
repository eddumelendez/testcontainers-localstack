package com.example.cognito;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolClientRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIfEnvironmentVariable(named = "LOCALSTACK_API_KEY", matches = ".+")
class CognitoApplicationTests {

	@Container
	private static final LocalStackContainer localstack = (LocalStackContainer) new LocalStackContainer(
			DockerImageName.parse("localstack/localstack-pro:3.0.2"))
		.withEnv("SERVICES", "cognito-idp")
		.withEnv("LOCALSTACK_API_KEY", System.getenv("LOCALSTACK_API_KEY"));

	private static String userPoolId;

	private static String appClientId;

	private static String accessToken;

	@LocalServerPort
	private int port;

	@BeforeAll
	static void beforeAll() {
		var credentialsProvider = StaticCredentialsProvider
			.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
		var cognitoClient = CognitoIdentityProviderClient.builder()
			.credentialsProvider(credentialsProvider)
			.region(Region.of(localstack.getRegion()))
			.endpointOverride(localstack.getEndpoint())
			.build();

		var userPoolResponse = cognitoClient
			.createUserPool(CreateUserPoolRequest.builder().poolName("awspring-test").build());
		userPoolId = userPoolResponse.userPool().id();

		var userPoolClientResponse = cognitoClient.createUserPoolClient(CreateUserPoolClientRequest.builder()
			.clientName("awspring-test-client")
			.userPoolId(userPoolId)
			.generateSecret(true)
			.build());
		appClientId = userPoolClientResponse.userPoolClient().clientId();
		var appsecret = userPoolClientResponse.userPoolClient().clientSecret();

		cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
			.userPoolId(userPoolId)
			.username("testuser@test.com")
			.temporaryPassword("testP@ssw0rd")
			.userAttributes(AttributeType.builder().name("email").value("testuser@test.com").build(),
					AttributeType.builder().name("email_verified").value("true").build())
			.build());

		var initiateAuthResponse = cognitoClient.initiateAuth(InitiateAuthRequest.builder()
			.authFlow(AuthFlowType.USER_PASSWORD_AUTH)
			.clientId(appClientId)
			.authParameters(Map.of("USERNAME", "testuser@test.com", "PASSWORD", "testP@ssw0rd"))
			.build());
		accessToken = initiateAuthResponse.authenticationResult().accessToken();
	}

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
				() -> "http://localhost.localstack.cloud:" + localstack.getMappedPort(4566) + "/" + userPoolId);
	}

	@Test
	void contextLoads() {
		var appBaseUrl = "http://localhost:" + this.port;

		var restClient = RestClient.builder().baseUrl(appBaseUrl).build();

		var unsecuredResponse = restClient.get().uri(appBaseUrl).retrieve().toBodilessEntity();
		assertThat(unsecuredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

		assertThatExceptionOfType(HttpClientErrorException.Unauthorized.class)
			.isThrownBy(() -> restClient.get().uri("/topsecret").retrieve().toBodilessEntity());

		var securedResponse = restClient.get()
			.uri("/topsecret")
			.headers(h -> h.setBearerAuth(accessToken))
			.retrieve()
			.toBodilessEntity();
		assertThat(securedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	static class LocalStackContainer extends org.testcontainers.containers.localstack.LocalStackContainer {

		private static final String STARTER_SCRIPT = "/testcontainers_start.sh";

		public LocalStackContainer(DockerImageName dockerImageName) {
			super(dockerImageName);
			withEnv("SERVICES", "cognito-idp");
			withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh"));
			setCommand("-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);
		}

		@Override
		protected void containerIsStarting(InspectContainerResponse containerInfo) {
			var command = """
					#!/bin/bash
					export LOCALSTACK_HOST=:%d
					/usr/local/bin/docker-entrypoint.sh
					""".formatted(getMappedPort(4566));
			copyFileToContainer(Transferable.of(command, 0777), STARTER_SCRIPT);
		}

	}

}
