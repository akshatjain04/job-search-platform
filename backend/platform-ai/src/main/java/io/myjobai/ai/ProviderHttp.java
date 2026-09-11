package io.myjobai.ai;

import io.myjobai.application.IntegrationException;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class ProviderHttp {
    private final HttpClient client;private final JsonMapper json;
    public ProviderHttp(JsonMapper json){this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build(),json);}
    public ProviderHttp(HttpClient client,JsonMapper json){this.client=client;this.json=json;}
    public JsonNode post(URI uri,Map<String,String> headers,Object body,ProviderSettings settings){
        String payload=json.writeValueAsString(body);if(payload.length()>250000)throw failure("AI_INPUT_LIMIT","AI input exceeds 250000 characters",false);
        for(int attempt=1;attempt<=settings.attempts();attempt++){
            var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(settings.timeoutSeconds())).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload));headers.forEach(builder::header);
            CompletableFuture<HttpResponse<String>> pending=client.sendAsync(builder.build(),HttpResponse.BodyHandlers.ofString());
            try{
                var response=pending.get(settings.timeoutSeconds(),TimeUnit.SECONDS);int status=response.statusCode();
                if(status>=200&&status<300){if(response.body().length()>1000000)throw failure("AI_OUTPUT_LIMIT","Provider response exceeds output limit",false);return json.readTree(response.body());}
                boolean retry=status==429||status>=500;
                if(!retry||attempt==settings.attempts())throw failure(status==401||status==403?"AI_AUTH":status==429?"AI_RATE_LIMIT":status>=500?"AI_UNAVAILABLE":"AI_REQUEST","Selected AI provider returned HTTP "+status+"; check model, credentials and limits",retry);
            }catch(InterruptedException e){pending.cancel(true);Thread.currentThread().interrupt();throw failure("AI_CANCELLED","AI request cancelled",false);}
            catch(TimeoutException e){pending.cancel(true);if(attempt==settings.attempts())throw failure("AI_TIMEOUT","AI provider request timed out",true);}
            catch(ExecutionException e){pending.cancel(true);if(attempt==settings.attempts())throw failure("AI_CONNECTION","AI provider connection failed",true);}
            catch(IntegrationException e){throw e;}
            catch(RuntimeException e){throw failure("AI_RESPONSE","AI provider returned malformed JSON",false);}
            try{Thread.sleep(Math.min(4000,500L<<(attempt-1)));}catch(InterruptedException e){Thread.currentThread().interrupt();throw failure("AI_CANCELLED","AI request cancelled",false);}
        }
        throw failure("AI_UNAVAILABLE","AI request failed",true);
    }
    private static IntegrationException failure(String code,String message,boolean retry){return new IntegrationException(code,message,retry,false);}
}
