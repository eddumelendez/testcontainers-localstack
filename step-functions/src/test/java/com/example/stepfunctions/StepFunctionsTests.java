package com.example.stepfunctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.CreateStateMachineRequest;
import software.amazon.awssdk.services.sfn.model.CreateStateMachineResponse;
import software.amazon.awssdk.services.sfn.model.DescribeExecutionRequest;
import software.amazon.awssdk.services.sfn.model.DescribeExecutionResponse;
import software.amazon.awssdk.services.sfn.model.ExecutionStatus;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;
import software.amazon.awssdk.services.sfn.model.StateMachineType;

@Testcontainers
class StepFunctionsTests {

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.7.0"));

	@Test
	void createAndExecuteStateMachine() throws Exception {
		try (SfnClient sfnClient = SfnClient.builder()
			.endpointOverride(localstack.getEndpoint())
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.region(Region.of(localstack.getRegion()))
			.build()) {

			String roleArn = createIamRole();

			String stateMachineDefinition = """
					{
					  "Comment": "A Hello World example",
					  "StartAt": "HelloWorld",
					  "States": {
					    "HelloWorld": {
					      "Type": "Pass",
					      "Result": "Hello World!",
					      "End": true
					    }
					  }
					}
					""";

			CreateStateMachineRequest createRequest = CreateStateMachineRequest.builder()
				.name("HelloWorldStateMachine")
				.definition(stateMachineDefinition)
				.roleArn(roleArn)
				.type(StateMachineType.STANDARD)
				.build();

			CreateStateMachineResponse createResponse = sfnClient.createStateMachine(createRequest);
			assertThat(createResponse.stateMachineArn()).isNotNull();

			Map<String, String> input = new HashMap<>();
			input.put("name", "World");

			StartExecutionRequest startRequest = StartExecutionRequest.builder()
				.stateMachineArn(createResponse.stateMachineArn())
				.name("test-execution")
				.input("{\"name\": \"World\"}")
				.build();

			StartExecutionResponse startResponse = sfnClient.startExecution(startRequest);
			assertThat(startResponse.executionArn()).isNotNull();

			await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until(() -> {
				DescribeExecutionResponse execution = sfnClient.describeExecution(
						DescribeExecutionRequest.builder().executionArn(startResponse.executionArn()).build());
				return execution.status() == ExecutionStatus.SUCCEEDED;
			});

			DescribeExecutionResponse finalExecution = sfnClient.describeExecution(
					DescribeExecutionRequest.builder().executionArn(startResponse.executionArn()).build());

			assertThat(finalExecution.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
			assertThat(finalExecution.output()).isEqualTo("\"Hello World!\"");
		}
	}

	private String createIamRole() {
		try (IamClient iamClient = IamClient.builder()
			.endpointOverride(localstack.getEndpoint())
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.region(Region.of(localstack.getRegion()))
			.build()) {

			String trustPolicy = """
					{
					  "Version": "2012-10-17",
					  "Statement": [
					    {
					      "Effect": "Allow",
					      "Principal": {
					        "Service": "states.amazonaws.com"
					      },
					      "Action": "sts:AssumeRole"
					    }
					  ]
					}
					""";

			CreateRoleRequest roleRequest = CreateRoleRequest.builder()
				.roleName("StepFunctionsRole")
				.assumeRolePolicyDocument(trustPolicy)
				.build();

			CreateRoleResponse roleResponse = iamClient.createRole(roleRequest);
			return roleResponse.role().arn();
		}
	}

}