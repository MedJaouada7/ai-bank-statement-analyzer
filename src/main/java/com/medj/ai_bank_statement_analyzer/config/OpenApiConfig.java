package com.medj.ai_bank_statement_analyzer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("AI Bank Statement Analyzer API")
                                .description(
                                        "Statement ingestion, AI categorization and spending insights")
                                .version("v0.1.0"));
    }
}
