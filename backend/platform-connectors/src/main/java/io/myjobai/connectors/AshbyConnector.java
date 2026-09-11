package io.myjobai.connectors;

import io.myjobai.application.Ports;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

public final class AshbyConnector extends FeedConnector {
  public AshbyConnector(Function<String, String> fetch, JsonMapper json, Clock clock) {
    super(fetch, json, clock);
  }

  public String key() {
    return "ashby";
  }

  protected String endpoint(String board) {
    return "https://api.ashbyhq.com/posting-api/job-board/" + board;
  }

  protected List<Ports.DiscoveredJob> parse(String board, JsonNode payload) {
    var jobs = new ArrayList<Ports.DiscoveredJob>();
    for (var node : payload.path("jobs"))
      jobs.add(
          job(
              node.path("id").asText(node.path("jobUrl").asText()),
              node.path("title").asText(),
              board,
              node.path("location").asText(""),
              node.path("descriptionPlain").asText(node.path("descriptionHtml").asText()),
              node.path("isRemote").asBoolean(),
              node.path("employmentType").asText(""),
              node.path("jobUrl").asText(),
              node.path("publishedAt").asText()));
    return jobs;
  }
}
