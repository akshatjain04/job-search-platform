package io.myjobai.ai;

import io.myjobai.application.*;
import java.net.URI;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class OpenAiLlmProvider extends StructuredProvider {
  private final URI endpoint;

  public OpenAiLlmProvider(ProviderSettings s, ProviderHttp http, JsonMapper json) {
    this(s, http, json, URI.create("https://api.openai.com/v1/responses"));
  }

  public OpenAiLlmProvider(ProviderSettings s, ProviderHttp http, JsonMapper json, URI endpoint) {
    super(s, http, json);
    this.endpoint = endpoint;
  }

  public Ports.Completion generate(Ports.LlmRequest request) {
    long start = System.nanoTime();
    var result =
        http.post(
            endpoint,
            Map.of("Authorization", "Bearer " + settings.apiKey()),
            Map.of(
                "model",
                settings.model(request.tier()),
                "instructions",
                system(request),
                "input",
                input(request),
                "store",
                false,
                "max_output_tokens",
                settings.maxOutputTokens(),
                "text",
                Map.of(
                    "format",
                    Map.of(
                        "type",
                        "json_schema",
                        "name",
                        "platform_output",
                        "strict",
                        true,
                        "schema",
                        request.schema()))),
            settings);
    if (!result.path("status").asText().equals("completed"))
      throw new IntegrationException(
          "AI_INCOMPLETE", "OpenAI output was not completed", false, false);
    StringBuilder text = new StringBuilder();
    result
        .path("output")
        .forEach(
            item ->
                item.path("content")
                    .forEach(
                        part -> {
                          if (part.path("type").asText().equals("output_text"))
                            text.append(part.path("text").asText());
                        }));
    return finish(
        request,
        text.toString(),
        result.path("usage").path("input_tokens").asLong(),
        result.path("usage").path("output_tokens").asLong(),
        start);
  }
}
