package io.myjobai.connectors;

import io.myjobai.application.Ports;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;

public final class GreenhouseConnector extends FeedConnector {
    public GreenhouseConnector(Function<String,String> fetch,JsonMapper json,Clock clock){super(fetch,json,clock);}
    public String key(){return "greenhouse";}
    protected String endpoint(String board){return "https://boards-api.greenhouse.io/v1/boards/"+board+"/jobs?content=true";}
    protected List<Ports.DiscoveredJob> parse(String board,JsonNode payload){var jobs=new ArrayList<Ports.DiscoveredJob>();for(var node:payload.path("jobs")){String location=node.path("location").path("name").asText("");jobs.add(job(node.path("id").asText(),node.path("title").asText(),node.path("company_name").asText(board),location,node.path("content").asText(),location.toLowerCase().contains("remote"),"",node.path("absolute_url").asText(),node.path("first_published").asText(node.path("updated_at").asText())));}return jobs;}
}
