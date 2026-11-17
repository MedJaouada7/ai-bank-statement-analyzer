package com.medj.ai_bank_statement_analyzer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packagesOf = AiBankStatementAnalyzerApplication.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTests {

    @ArchTest
    static final ArchRule domainIsFrameworkFree =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "org.hibernate..",
                            "com.fasterxml.jackson..",
                            "tools.jackson..")
                    .because("domain code must stay pure and unit-testable")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnApplication =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..application..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule innerLayersDoNotDependOnAdapters =
            noClasses()
                    .that()
                    .resideInAnyPackage("..domain..", "..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..adapter..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule springAiOnlyInLlmAdapters =
            noClasses()
                    .that()
                    .resideOutsideOfPackages("..adapter.out.llm..", "..adapter.out.ai..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("org.springframework.ai..")
                    .because("Spring AI dependencies must be restricted to adapter packages")
                    .allowEmptyShould(true);
}
