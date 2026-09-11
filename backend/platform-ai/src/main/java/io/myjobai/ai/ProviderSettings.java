package io.myjobai.ai;

import io.myjobai.application.Ports;
import java.util.*;

public record ProviderSettings(
    String key,
    String apiKey,
    Map<Ports.Tier, String> models,
    int timeoutSeconds,
    int attempts,
    int maxOutputTokens,
    double inputCostPerMillion,
    double outputCostPerMillion) {
  public ProviderSettings {
    models = Map.copyOf(models);
    if (timeoutSeconds < 1
        || timeoutSeconds > 120
        || attempts < 1
        || attempts > 3
        || maxOutputTokens < 1
        || maxOutputTokens > 16000)
      throw new IllegalArgumentException(
          "AI timeout must be 1..120 seconds, attempts 1..3 and output cap 1..16000");
  }

  public void validateLive() {
    String credential =
        switch (key) {
          case "gemini" -> "GEMINI_API_KEY";
          case "openai" -> "OPENAI_API_KEY";
          case "claude" -> "ANTHROPIC_API_KEY";
          default ->
              throw new IllegalArgumentException(
                  "Unknown LLM_PROVIDER '" + key + "'; supported: gemini, openai, claude");
        };
    if (apiKey == null || apiKey.isBlank())
      throw new IllegalArgumentException("Selected provider requires " + credential);
    for (var tier : Ports.Tier.values())
      if (models.getOrDefault(tier, "").isBlank())
        throw new IllegalArgumentException(
            "Selected provider requires " + key.toUpperCase(Locale.ROOT) + "_MODEL_" + tier);
  }

  public String model(Ports.Tier tier) {
    String model = models.get(tier);
    if (model == null || model.isBlank())
      throw new IllegalArgumentException("Missing model for " + key + "/" + tier);
    return model;
  }

  public static ProviderSettings from(String key, Map<String, String> env) {
    String prefix = key.toUpperCase(Locale.ROOT);
    var models = new EnumMap<Ports.Tier, String>(Ports.Tier.class);
    for (var tier : Ports.Tier.values())
      models.put(tier, env.getOrDefault(prefix + "_MODEL_" + tier, ""));
    return new ProviderSettings(
        key,
        env.getOrDefault(key.equals("claude") ? "ANTHROPIC_API_KEY" : prefix + "_API_KEY", ""),
        models,
        Integer.parseInt(env.getOrDefault("LLM_TIMEOUT_SECONDS", "45")),
        Integer.parseInt(env.getOrDefault("LLM_MAX_ATTEMPTS", "3")),
        Integer.parseInt(env.getOrDefault("LLM_MAX_OUTPUT_TOKENS", "4000")),
        Double.parseDouble(env.getOrDefault(prefix + "_INPUT_COST_PER_MILLION", "0")),
        Double.parseDouble(env.getOrDefault(prefix + "_OUTPUT_COST_PER_MILLION", "0")));
  }

  @Override
  public String toString() {
    return "ProviderSettings[provider=" + key + ", credentials=REDACTED]";
  }
}
