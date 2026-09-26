package com.temaconsulting.agente_iso;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.TimeZone;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
	}

}
