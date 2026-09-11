package io.myjobai.connectors;

import io.myjobai.application.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class BraveWebResearch implements Ports.WebResearch {
  private final SafeWebClient http;
  private final JsonMapper json;
  private final String apiKey;

  public BraveWebResearch(SafeWebClient http, JsonMapper json, String apiKey) {
    this.http = http;
    this.json = json;
    this.apiKey = apiKey;
  }

  public List<Ports.SearchResult> search(String query) {
    if (apiKey == null || apiKey.isBlank())
      throw new IntegrationException(
          "RESEARCH_CONFIG",
          "Public web research requires BRAVE_SEARCH_API_KEY; manual contact entry remains available",
          false,
          false);
    var payload =
        json.readTree(
            http.get(
                "https://api.search.brave.com/res/v1/web/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&count=5",
                Map.of("X-Subscription-Token", apiKey, "Accept", "application/json")));
    var results = new ArrayList<Ports.SearchResult>();
    for (var item : payload.path("web").path("results"))
      results.add(
          new Ports.SearchResult(
              item.path("title").asText(),
              item.path("url").asText(),
              item.path("description").asText()));
    return List.copyOf(results);
  }

  public String publicPage(String url) {
    return http.get(url);
  }
}
