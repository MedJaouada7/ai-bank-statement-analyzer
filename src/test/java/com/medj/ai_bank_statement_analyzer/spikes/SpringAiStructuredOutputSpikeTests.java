package com.medj.ai_bank_statement_analyzer.spikes;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.medj.ai_bank_statement_analyzer.support.AbstractIntegrationTest;
import com.medj.ai_bank_statement_analyzer.support.OllamaWireMock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Manual spike. Run with OLLAMA_LIVE=true and a live Ollama on localhost:11434. The spike compares
 * the Spring AI modes for structured output: prompt-based vs native.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE", matches = "true")
class SpringAiStructuredOutputSpikeTests extends AbstractIntegrationTest {

    enum SpikeCategory {
        COFFEE,
        GROCERIES,
        DINING,
        TRANSPORT,
        SHOPPING,
        UTILITIES,
        OTHER
    }

    record CategoryResult(SpikeCategory category) {}

    record Case(String text, SpikeCategory expected) {}

    record Shot(String text, SpikeCategory category) {}

    // System message for the LLM
    static final String SYSTEM =
            """
            You classify bank transactions into exactly one category.
            - COFFEE: cafes and coffee shops
            - GROCERIES: supermarkets and food stores
            - DINING: restaurants, fast food, bars
            - TRANSPORT: rides, fuel, trains, transit, parking
            - SHOPPING: retail stores and online marketplaces
            - UTILITIES: electricity, gas, water, internet, phone
            - OTHER: anything else, or unclear
            Reply with JSON only.
            """;

    // Example shots for few-shot learning
    static final List<Shot> SHOTS =
            List.of(
                    new Shot("AMZN MKTP", SpikeCategory.SHOPPING),
                    new Shot("UBER TRIP", SpikeCategory.TRANSPORT),
                    new Shot("MCDONALDS", SpikeCategory.DINING),
                    new Shot("EDF", SpikeCategory.UTILITIES));

    // Same 19 frozen cases as tools/spikes/ollama-classify.ps1 (normalized form)
    static final List<Case> CASES =
            List.of(
                    new Case("BLUE BOTTLE", SpikeCategory.COFFEE),
                    new Case("STARBUCKS", SpikeCategory.COFFEE),
                    new Case("CARREFOUR MARKET", SpikeCategory.GROCERIES),
                    new Case("LIDL", SpikeCategory.GROCERIES),
                    new Case("WHOLE FOODS", SpikeCategory.GROCERIES),
                    new Case("SNCF", SpikeCategory.TRANSPORT),
                    new Case("TOTALENERGIES", SpikeCategory.TRANSPORT),
                    new Case("LYFT", SpikeCategory.TRANSPORT),
                    new Case("ORANGE", SpikeCategory.UTILITIES),
                    new Case("ENGIE", SpikeCategory.UTILITIES),
                    new Case("COMCAST", SpikeCategory.UTILITIES),
                    new Case("ZARA", SpikeCategory.SHOPPING),
                    new Case("IKEA", SpikeCategory.SHOPPING),
                    new Case("DECATHLON", SpikeCategory.SHOPPING),
                    new Case("PIZZA HUT", SpikeCategory.DINING),
                    new Case("CHIPOTLE", SpikeCategory.DINING),
                    new Case("LE COMPTOIR DU RELAIS", SpikeCategory.DINING),
                    new Case("M DUPONT LOYER", SpikeCategory.OTHER),
                    new Case("RETRAIT DAB", SpikeCategory.OTHER));

    // WireMock extension to proxy to a real Ollama instance on localhost:11434
    @RegisterExtension static WireMockExtension ollama = OllamaWireMock.extension();

    @DynamicPropertySource
    static void ollamaUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url", ollama::baseUrl);
    }

    @Autowired ChatClient.Builder builder;

    @BeforeEach
    void proxyToRealOllama() {
        ollama.stubFor(any(anyUrl()).willReturn(aResponse().proxiedFrom("http://localhost:11434")));
    }

    // Compare the two structured output modes: prompt-based vs native
    @Test
    void compareStructuredOutputModes() {
        ChatClient client = builder.build();
        classify(client, "WARM UP", false); // to load the model, excluded from the timing

        for (boolean nativeMode : new boolean[] {false, true}) {
            ollama.resetRequests();
            classify(client, "BLUE BOTTLE", nativeMode);
            printRequest(nativeMode ? "native" : "prompt-based");

            int valid = 0;
            int correct = 0;
            List<Long> millis = new ArrayList<>();
            for (Case c : CASES) {
                long start = System.nanoTime();
                SpikeCategory got = classify(client, c.text(), nativeMode);
                millis.add((System.nanoTime() - start) / 1_000_000);
                if (got != null) {
                    valid++;
                    if (got == c.expected()) {
                        correct++;
                    } else {
                        System.out.printf(
                                "   MISS %-24s expected=%s got=%s%n", c.text(), c.expected(), got);
                    }
                }
            }
            millis.sort(Long::compare);
            System.out.printf(
                    "SPIKE mode=%s valid=%d/%d correct=%d/%d median=%dms%n",
                    nativeMode ? "native" : "prompt-based",
                    valid,
                    CASES.size(),
                    correct,
                    CASES.size(),
                    millis.get(millis.size() / 2));
        }
    }

    // Classify a single text using the given ChatClient and mode (native or prompt-based)
    private SpikeCategory classify(ChatClient client, String text, boolean nativeMode) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM));
        for (Shot shot : SHOTS) {
            messages.add(new UserMessage(shot.text()));
            messages.add(new AssistantMessage("{\"category\":\"" + shot.category() + "\"}"));
        }
        messages.add(new UserMessage(text));

        try {
            var request =
                    client.prompt()
                            .messages(messages)
                            .options(
                                    OllamaChatOptions.builder()
                                            .temperature(0.0)
                                            .seed(42)
                                            .numCtx(2048)
                                            .numPredict(40)
                                            .keepAlive("10m"));
            CategoryResult result =
                    nativeMode
                            ? request.call()
                                    .entity(
                                            CategoryResult.class,
                                            o -> o.useProviderStructuredOutput())
                            : request.call().entity(CategoryResult.class);
            return result.category();
        } catch (RuntimeException e) {
            System.out.println("   INVALID for '" + text + "': " + e.getClass().getSimpleName());
            return null;
        }
    }

    // Print the last request made to the Ollama API, showing whether the "format" field was present
    // and whether it contained "enum"
    private void printRequest(String mode) {
        List<LoggedRequest> requests = ollama.findAll(postRequestedFor(urlEqualTo("/api/chat")));
        String body = requests.get(requests.size() - 1).getBodyAsString();
        System.out.println(
                "REQUEST mode="
                        + mode
                        + " (format field present: "
                        + body.contains("\"format\"")
                        + ", enum in format: "
                        + body.contains("\"enum\"")
                        + ")");
        System.out.println(body.length() > 900 ? body.substring(0, 900) + "..." : body);
    }
}
