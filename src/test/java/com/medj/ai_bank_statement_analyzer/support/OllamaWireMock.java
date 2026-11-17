package com.medj.ai_bank_statement_analyzer.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/* Utility class for creating Ollama wiremock stubs. */
public final class OllamaWireMock {

    private OllamaWireMock() {}

    public static WireMockExtension extension() {
        return WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();
    }

    /** Non-streaming /api/chat reply whose assistant message is {@code content}. */
    public static void stubChat(WireMockExtension server, String content) {
        server.stubFor(
                post(urlEqualTo("/api/chat"))
                        .willReturn(
                                okJson(
                                        """
                {"model":"stub","created_at":"2026-01-01T00:00:00Z",
                 "message":{"role":"assistant","content":"%s"},
                 "done":true,"done_reason":"stop",
                 "total_duration":1,"load_duration":1,
                 "prompt_eval_count":10,"prompt_eval_duration":1,
                 "eval_count":5,"eval_duration":1}
                """
                                                .formatted(escape(content)))));
    }

    /** Simulates Ollama being down (for the LLM_UNAVAILABLE scenario). */
    public static void stubChatUnavailable(WireMockExtension server) {
        server.stubFor(
                post(urlEqualTo("/api/chat"))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    /** Constant-vector embedding reply; TODO : replace with real fixtures when testing kNN. */
    public static void stubEmbedding(WireMockExtension server, int dimensions) {
        String vector =
                IntStream.range(0, dimensions)
                        .mapToObj(i -> "0.01")
                        .collect(Collectors.joining(","));
        server.stubFor(
                post(urlEqualTo("/api/embed"))
                        .willReturn(
                                okJson(
                                        """
                {"model":"stub","embeddings":[[%s]],
                 "total_duration":1,"load_duration":1,"prompt_eval_count":1}
                """
                                                .formatted(vector))));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
