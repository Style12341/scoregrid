package com.scoregrid.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Pinned to match the compose stack. "postgres:latest" drifts under you:
		// a major-version bump would change behaviour with no code change here.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

}
