package com.medj.ai_bank_statement_analyzer.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/* Tests that the OllamaWireMock stubs work correctly. */
class OllamaWireMockTests {

    @RegisterExtension static WireMockExtension ollama = OllamaWireMock.extension();

    /* Tests that the chat stub returns the configured content. */
    @Test
    void chatStubReturnsConfiguredContent() throws Exception {
        OllamaWireMock.stubChat(ollama, "{\"category\":\"Dining\"}");

        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(URI.create(ollama.baseUrl() + "/api/chat"))
                                        .POST(
                                                HttpRequest.BodyPublishers.ofString(
                                                        "{\"model\":\"stub\",\"stream\":false}"))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Dining");
    }
}
