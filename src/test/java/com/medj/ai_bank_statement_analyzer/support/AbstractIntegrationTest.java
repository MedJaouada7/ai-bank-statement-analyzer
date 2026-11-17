package com.medj.ai_bank_statement_analyzer.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {}
