package io.myjobai.ai;

import io.myjobai.application.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.util.*;

public final class ClaudeLlmProvider extends StructuredProvider {
    private final URI endpoint;
    public ClaudeLlmProvider(ProviderSettings s,ProviderHttp http,JsonMapper json){this(s,http,json,URI.create("https://api.anthropic.com/v1/messages"));}
    public ClaudeLlmProvider(ProviderSettings s,ProviderHttp http,JsonMapper json,URI endpoint){super(s,http,json);this.endpoint=endpoint;}
    public Ports.Completion generate(Ports.LlmRequest request){
        long start=System.nanoTime();
        var result=http.post(endpoint,Map.of("x-api-key",settings.apiKey(),"anthropic-version","2023-06-01"),Map.of("model",settings.model(request.tier()),"max_tokens",settings.maxOutputTokens(),"system",system(request),"messages",List.of(Map.of("role","user","content",input(request))),"output_config",Map.of("format",Map.of("type","json_schema","schema",request.schema()))),settings);
        if(!result.path("stop_reason").asText().equals("end_turn"))throw new IntegrationException("AI_INCOMPLETE","Claude output was blocked or truncated",false,false);
        StringBuilder text=new StringBuilder();result.path("content").forEach(part->{if(part.path("type").asText().equals("text"))text.append(part.path("text").asText());});
        return finish(request,text.toString(),result.path("usage").path("input_tokens").asLong(),result.path("usage").path("output_tokens").asLong(),start);
    }
}
