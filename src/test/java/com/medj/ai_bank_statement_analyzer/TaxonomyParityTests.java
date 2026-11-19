package com.medj.ai_bank_statement_analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import com.medj.ai_bank_statement_analyzer.shared.taxonomy.Category;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/** Tests to ensure the Java enum matches the YAML taxonomy definition. */
class TaxonomyParityTests {

    @Test
    @SuppressWarnings("unchecked")
    void javaEnumMatchesYamlTaxonomy() throws Exception {
        try (InputStream in = new ClassPathResource("taxonomy/taxonomy-v1.yml").getInputStream()) {
            Map<String, Object> doc = new Yaml().load(in);
            List<Map<String, Object>> categories =
                    (List<Map<String, Object>>) doc.get("categories");

            assertThat(categories)
                    .extracting(c -> c.get("id"))
                    .containsExactlyInAnyOrder(
                            Arrays.stream(Category.values()).map(Enum::name).toArray());

            assertThat(categories)
                    .allSatisfy(
                            c -> {
                                assertThat((String) c.get("definition")).isNotBlank();
                                assertThat((String) c.get("direction"))
                                        .isIn("debit", "credit", "both");
                            });
        }
    }
}
