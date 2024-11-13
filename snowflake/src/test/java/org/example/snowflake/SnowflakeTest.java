package org.example.snowflake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "LOCALSTACK_AUTH_TOKEN", matches = ".+")
public class SnowflakeTest {

	@Container
	private static final GenericContainer<?> container = new GenericContainer<>("localstack/snowflake:0.2.3")
		.withExposedPorts(4566)
		.withEnv("LOCALSTACK_AUTH_TOKEN", System.getenv("LOCALSTACK_AUTH_TOKEN"))
		.waitingFor(Wait.forLogMessage(".*Ready.*", 1));

	@Test
	void test() throws Exception {
		String url = "jdbc:snowflake://snowflake.localhost.localstack.cloud:" + container.getMappedPort(4566);
		Properties prop = new Properties();
		prop.put("user", "test");
		prop.put("password", "test");
		prop.put("account", "test");
		prop.put("database", "test");

		try (Connection conn = DriverManager.getConnection(url, prop)) {
			Statement stat = conn.createStatement();
			stat.execute("create table profile (name VARCHAR(25) NOT NULL PRIMARY KEY)");
			stat.execute("insert into profile (name) values ('test')");
			ResultSet res = stat.executeQuery("select name from profile");
			res.next();
			assertThat(res.getString(1)).isEqualTo("test");
		}
	}

}
