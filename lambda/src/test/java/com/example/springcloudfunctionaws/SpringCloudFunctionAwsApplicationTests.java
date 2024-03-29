package com.example.springcloudfunctionaws;

import io.restassured.RestAssured;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ResourceReaper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SpringCloudFunctionAwsApplicationTests {

	static String jar = buildJar();

	static Network network = Network.newNetwork();

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine").withNetwork(network)
		.withNetworkAliases("postgres");

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.2.0"))
		.withNetwork(network)
		.withEnv("LOCALSTACK_HOST", "localhost.localstack.cloud")
		.withEnv("LAMBDA_DOCKER_NETWORK", ((Network.NetworkImpl) network).getName())
		.withNetworkAliases("localstack")
		.withEnv("LAMBDA_DOCKER_FLAGS", testcontainersLabels());

	static String testcontainersLabels() {
		return Stream
			.of(DockerClientFactory.DEFAULT_LABELS.entrySet().stream(),
					ResourceReaper.instance().getLabels().entrySet().stream())
			.flatMap(Function.identity())
			.map(entry -> String.format("-l %s=%s", entry.getKey(), entry.getValue()))
			.collect(Collectors.joining(" "));
	}

	static String buildJar() {
		try {
			var properties = new Properties();
			properties.setProperty("skipTests", "true");

			var defaultInvocationRequest = new DefaultInvocationRequest();
			if (StringUtils.hasText(System.getenv("MAVEN_HOME"))) {
				defaultInvocationRequest.setMavenHome(new File(System.getenv("MAVEN_HOME")));
			}
			defaultInvocationRequest.setJavaHome(new File(System.getenv("JAVA_HOME")));
			defaultInvocationRequest.setPomFile(Paths.get("pom.xml").toFile());
			defaultInvocationRequest.setGoals(List.of("package"));
			defaultInvocationRequest.setProperties(properties);
			var appInvoker = new DefaultInvoker();
			appInvoker.execute(defaultInvocationRequest);
		}
		catch (MavenInvocationException e) {
			throw new RuntimeException("Could not build jar", e);
		}
		return "target/lambda-0.0.1-SNAPSHOT-aws.jar";
	}

	@Test
	void contextLoads() throws IOException {
		var fnName = "uppercase-fn";
		var envVars = Map.ofEntries(Map.entry("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/test"),
				Map.entry("SPRING_DATASOURCE_USERNAME", "test"), Map.entry("SPRING_DATASOURCE_PASSWORD", "test"));
		var createFunctionRequest = CreateFunctionRequest.builder()
			.functionName(fnName)
			.runtime(Runtime.JAVA21)
			.role("arn:aws:iam::123456789012:role/irrelevant")
			.packageType(PackageType.ZIP)
			.code(FunctionCode.builder()
				.zipFile(SdkBytes.fromByteArray(FileUtils.readFileToByteArray(new File(jar))))
				.build())
			.timeout(10)
			.handler("org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest")
			.environment(Environment.builder().variables(envVars).build())
			.build();

		var lambdaClient = LambdaClient.builder()
			.region(Region.of(localstack.getRegion()))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.endpointOverride(localstack.getEndpoint())
			.build();

		var createFunctionResponse = lambdaClient.createFunction(createFunctionRequest);
		var waiterResponse = lambdaClient.waiter()
			.waitUntilFunctionActive(GetFunctionConfigurationRequest.builder().functionName(fnName).build());

		var createFunctionUrlConfigRequest = CreateFunctionUrlConfigRequest.builder()
			.functionName(fnName)
			.authType(FunctionUrlAuthType.NONE)
			.build();
		var createFunctionUrlConfigResponse = lambdaClient.createFunctionUrlConfig(createFunctionUrlConfigRequest);

		var functionUrl = createFunctionUrlConfigResponse.functionUrl()
			.replace("" + 4566, "" + localstack.getMappedPort(4566));
		var body = RestAssured.given().body("""
				{"name": "profile"}
				""").get(functionUrl).prettyPeek().andReturn().body();
		assertThat(body.asString()).isEqualTo("4");
	}

}
