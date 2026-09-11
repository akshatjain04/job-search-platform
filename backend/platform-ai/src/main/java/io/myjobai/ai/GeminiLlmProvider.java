package io.myjobai.ai;

import io.myjobai.application.*;
import java.net.URI;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class GeminiLlmProvider extends StructuredProvider {
  private final URI endpoint;

  public GeminiLlmProvider(ProviderSettings s, ProviderHttp http, JsonMapper json) {
    this(s, http, json, URI.create("https://generativelanguage.googleapis.com/v1beta/"));
  }

  public GeminiLlmProvider(ProviderSettings s, ProviderHttp http, JsonMapper json, URI endpoint) {
    super(s, http, json);
    this.endpoint = endpoint;
  }

  public Ports.Completion generate(Ports.LlmRequest request) {
    long start = System.nanoTime();
    String model = settings.model(request.tier());
    if (!model.matches("[A-Za-z0-9._-]+"))
      throw new IllegalArgumentException("Gemini model identifier contains unsupported characters");
    var result =
        http.post(
            endpoint.resolve("models/" + model + ":generateContent"),
            Map.of("x-goog-api-key", settings.apiKey()),
            Map.of(
                "systemInstruction",
                Map.of("parts", List.of(Map.of("text", system(request)))),
                "contents",
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", input(request))))),
                "generationConfig",
                Map.of(
                    "responseMimeType",
                    "application/json",
                    "responseJsonSchema",
                    request.schema(),
                    "maxOutputTokens",
                    settings.maxOutputTokens())),
            settings);
    var candidate = result.path("candidates").path(0);
    if (!candidate.path("finishReason").asText().equals("STOP"))
      throw new IntegrationException(
          "AI_INCOMPLETE", "Gemini output was blocked or truncated", false, false);
    StringBuilder text = new StringBuilder();
    candidate
        .path("content")
        .path("parts")
        .forEach(
            p -> {
              if (p.has("text")) text.append(p.path("text").asText());
            });
    return finish(
        request,
        text.toString(),
        result.path("usageMetadata").path("promptTokenCount").asLong(),
        result.path("usageMetadata").path("candidatesTokenCount").asLong(),
        start);
  }
}
