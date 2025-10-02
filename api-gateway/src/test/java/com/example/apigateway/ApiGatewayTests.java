package com.example.apigateway;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.apigateway.model.CreateResourceRequest;
import software.amazon.awssdk.services.apigateway.model.CreateResourceResponse;
import software.amazon.awssdk.services.apigateway.model.CreateRestApiRequest;
import software.amazon.awssdk.services.apigateway.model.CreateRestApiResponse;
import software.amazon.awssdk.services.apigateway.model.GetResourcesRequest;
import software.amazon.awssdk.services.apigateway.model.GetResourcesResponse;
import software.amazon.awssdk.services.apigateway.model.IntegrationType;
import software.amazon.awssdk.services.apigateway.model.PutIntegrationRequest;
import software.amazon.awssdk.services.apigateway.model.PutIntegrationResponseRequest;
import software.amazon.awssdk.services.apigateway.model.PutMethodRequest;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ApiGatewayTests {

	@Container
	private static final LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.9.0"));

	@Test
	void test() {
		AwsBasicCredentials awsCreds = AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey());
		try (ApiGatewayClient apiGateway = ApiGatewayClient.builder()
			.region(Region.of(localstack.getRegion()))
			.endpointOverride(localstack.getEndpoint())
			.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
			.build()) {

			CreateRestApiRequest createApiRequest = CreateRestApiRequest.builder().name("MyAPI").build();
			CreateRestApiResponse createApiResponse = apiGateway.createRestApi(createApiRequest);
			String restApiId = createApiResponse.id();

			GetResourcesRequest getResourcesRequest = GetResourcesRequest.builder().restApiId(restApiId).build();
			GetResourcesResponse getResourcesResponse = apiGateway.getResources(getResourcesRequest);
			String rootResourceId = getResourcesResponse.items().get(0).id();

			CreateResourceRequest createResourceRequest = CreateResourceRequest.builder()
				.restApiId(restApiId)
				.parentId(rootResourceId)
				.pathPart("example")
				.build();
			CreateResourceResponse createResourceResponse = apiGateway.createResource(createResourceRequest);
			String resourceId = createResourceResponse.id();

			PutMethodRequest putMethodRequest = PutMethodRequest.builder()
				.restApiId(restApiId)
				.resourceId(resourceId)
				.httpMethod("GET")
				.authorizationType("NONE")
				.build();
			apiGateway.putMethod(putMethodRequest);

			String requestTemplate = "{ \"statusCode\": 200 }";
			PutIntegrationRequest putIntegrationRequest = PutIntegrationRequest.builder()
				.restApiId(restApiId)
				.resourceId(resourceId)
				.httpMethod("GET")
				.type(IntegrationType.MOCK)
				.requestTemplates(Collections.singletonMap("application/json", requestTemplate))
				.build();
			apiGateway.putIntegration(putIntegrationRequest);

			String responseTemplate = "{\"message\": \"Hello, $input.params('name')\"}";
			PutIntegrationResponseRequest putIntegrationResponseRequest = PutIntegrationResponseRequest.builder()
				.restApiId(restApiId)
				.resourceId(resourceId)
				.httpMethod("GET")
				.statusCode("200")
				.responseTemplates(Collections.singletonMap("application/json", responseTemplate))
				.build();
			apiGateway.putIntegrationResponse(putIntegrationResponseRequest);

			CreateDeploymentRequest request = CreateDeploymentRequest.builder()
				.restApiId(restApiId)
				.stageName("test")
				.build();
			apiGateway.createDeployment(request);

			String body = given()
				.baseUri("http://%s.execute-api.localhost.localstack.cloud:%d".formatted(restApiId,
						localstack.getMappedPort(4566)))
				.queryParam("name", "World")
				.get("/test/example")
				.body()
				.asString();
			assertThat(body).contains("Hello, World");
		}
	}

}
