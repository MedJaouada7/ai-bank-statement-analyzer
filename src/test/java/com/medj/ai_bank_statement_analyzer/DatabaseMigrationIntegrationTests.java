package com.medj.ai_bank_statement_analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import com.medj.ai_bank_statement_analyzer.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class DatabaseMigrationIntegrationTests extends AbstractIntegrationTest {

    @Autowired JdbcClient jdbc;

    @Test
    void flywayInstallsPgvector() {
        Boolean installed =
                jdbc.sql("select exists (select 1 from pg_extension where extname = 'vector')")
                        .query(Boolean.class)
                        .single();
        assertThat(installed).isTrue();
    }
}
