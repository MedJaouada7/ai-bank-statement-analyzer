@ApplicationModule(
        allowedDependencies = {"ingestion", "enrichment", "assistant", "governance", "shared"})
package com.medj.ai_bank_statement_analyzer.evaluation;

import org.springframework.modulith.ApplicationModule;
