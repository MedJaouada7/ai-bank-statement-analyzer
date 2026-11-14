package com.medj.ai_bank_statement_analyzer;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private static final ApplicationModules MODULES =
            ApplicationModules.of(AiBankStatementAnalyzerApplication.class);

    @Test
    void listsDetectedModules() {
        MODULES.forEach(System.out::println);
    }

    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }
}
