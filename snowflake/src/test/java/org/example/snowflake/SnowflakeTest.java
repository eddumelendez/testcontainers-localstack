package org.example.snowflake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.datasource.username=test", "spring.datasource.password=test",
		"spring.datasource.driver-class-name=net.snowflake.client.jdbc.SnowflakeDriver", })
@Testcontainers
@EnabledIfEnvironmentVariable(named = "LOCALSTACK_AUTH_TOKEN", matches = ".+")
public class SnowflakeTest {

	@Container
	private static final GenericContainer<?> container = new GenericContainer<>("localstack/snowflake:1.5.0")
		.withExposedPorts(4566)
		.withEnv("LOCALSTACK_AUTH_TOKEN", System.getenv("LOCALSTACK_AUTH_TOKEN"))
		.waitingFor(Wait.forLogMessage(".*Ready.*", 1));

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:snowflake://snowflake.localhost.localstack.cloud:"
				+ container.getMappedPort(4566) + "/?db=test&schema=test&JDBC_QUERY_RESULT_FORMAT=JSON");
	}

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void test() {
		String name = this.jdbcClient.sql("select name from profile").query(String.class).single();
		assertThat(name).isEqualTo("test");
	}

}
