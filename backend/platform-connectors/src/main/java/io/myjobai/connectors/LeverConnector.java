package io.myjobai.connectors;

import io.myjobai.application.Ports;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.function.Function;

public final class LeverConnector extends FeedConnector {
    public LeverConnector(Function<String,String> fetch,JsonMapper json,Clock clock){super(fetch,json,clock);}
    public String key(){return "lever";}
    protected String endpoint(String board){return "https://api.lever.co/v0/postings/"+board+"?mode=json";}
    protected List<Ports.DiscoveredJob> parse(String board,JsonNode payload){var jobs=new ArrayList<Ports.DiscoveredJob>();for(var node:payload){var categories=node.path("categories");StringBuilder content=new StringBuilder(node.path("descriptionPlain").asText(node.path("description").asText()));for(var item:node.path("lists"))content.append(' ').append(item.path("text").asText()).append(' ').append(item.path("content").asText());jobs.add(job(node.path("id").asText(),node.path("text").asText(),board,categories.path("location").asText(""),content.toString(),node.path("workplaceType").asText().equals("remote"),categories.path("commitment").asText(""),node.path("hostedUrl").asText(),node.has("createdAt")?Instant.ofEpochMilli(node.path("createdAt").asLong()).toString():""));}return jobs;}
}
