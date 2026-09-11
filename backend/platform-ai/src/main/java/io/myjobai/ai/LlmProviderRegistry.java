package io.myjobai.ai;

import io.myjobai.application.Ports;
import java.util.*;
import java.util.function.Function;

public final class LlmProviderRegistry {
    private final Map<String,Function<ProviderSettings,Ports.LlmProvider>> factories;
    public LlmProviderRegistry(Map<String,Function<ProviderSettings,Ports.LlmProvider>> factories){this.factories=Map.copyOf(factories);}
    public Ports.LlmProvider select(Map<String,String> env){
        String key=env.getOrDefault("LLM_PROVIDER","gemini").toLowerCase(Locale.ROOT);
        if(!env.getOrDefault("LLM_FALLBACK_PROVIDERS","").isBlank())throw new IllegalArgumentException("Automatic fallback is disabled in V1; clear LLM_FALLBACK_PROVIDERS and explicitly select LLM_PROVIDER");
        var factory=factories.get(key);if(factory==null)throw new IllegalArgumentException("Unknown LLM_PROVIDER '"+key+"'; supported: "+String.join(", ",new TreeSet<>(factories.keySet())));
        var settings=ProviderSettings.from(key,env);settings.validateLive();return factory.apply(settings);
    }
}
