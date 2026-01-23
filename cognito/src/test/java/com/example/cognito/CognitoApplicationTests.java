package com.example.cognito;

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
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminResetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
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
@EnabledIfEnvironmentVariable(named = "LOCALSTACK_AUTH_TOKEN", matches = ".+")
class CognitoApplicationTests {

	@Container
	private static final LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack-pro:4.12.0"))
		.withEnv("SERVICES", "cognito-idp")
		.withEnv("LOCALSTACK_AUTH_TOKEN", System.getenv("LOCALSTACK_AUTH_TOKEN"));

	private static String userPoolId;

	private static String appClientId;

	private static String accessToken;

	@LocalServerPort
	private int port;

	@BeforeAll
	static void beforeAll() {
		var credentialsProvider = StaticCredentialsProvider
			.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
		try (var cognitoClient = CognitoIdentityProviderClient.builder()
			.credentialsProvider(credentialsProvider)
			.region(Region.of(localstack.getRegion()))
			.endpointOverride(localstack.getEndpoint())
			.build()) {

			var userPoolResponse = cognitoClient
				.createUserPool(CreateUserPoolRequest.builder().poolName("awspring-test").build());
			userPoolId = userPoolResponse.userPool().id();

			var userPoolClientResponse = cognitoClient.createUserPoolClient(CreateUserPoolClientRequest.builder()
				.clientName("awspring-test-client")
				.userPoolId(userPoolId)
				.build());
			appClientId = userPoolClientResponse.userPoolClient().clientId();

			cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
				.userPoolId(userPoolId)
				.username("testuser@test.com")
				.temporaryPassword("testP@ssw0rd")
				.userAttributes(AttributeType.builder().name("email").value("testuser@test.com").build(),
						AttributeType.builder().name("email_verified").value("true").build())
				.build());

			cognitoClient.adminResetUserPassword(AdminResetUserPasswordRequest.builder()
				.username("testuser@test.com")
				.userPoolId(userPoolId)
				.build());

			cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
				.username("testuser@test.com")
				.password("testP4ssw*rd")
				.userPoolId(userPoolId)
				.permanent(Boolean.TRUE)
				.build());

			var initiateAuthResponse = cognitoClient.initiateAuth(InitiateAuthRequest.builder()
				.authFlow(AuthFlowType.USER_PASSWORD_AUTH)
				.clientId(appClientId)
				.authParameters(Map.of("USERNAME", "testuser@test.com", "PASSWORD", "testP4ssw*rd"))
				.build());
			accessToken = initiateAuthResponse.authenticationResult().accessToken();
		}
	}

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		// registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () ->
		// localstack.getEndpoint() + "/" + userPoolId);
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
				() -> localstack.getEndpoint() + "/" + userPoolId + "/.well-known/jwks.json");
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

}
