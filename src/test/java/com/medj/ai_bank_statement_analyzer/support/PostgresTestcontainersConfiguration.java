package com.medj.ai_bank_statement_analyzer.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/* Configuration for setting up a PostgreSQL test container. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    private static final DockerImageName PGVECTOR =
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17")
                    .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(PGVECTOR);
    }
}
