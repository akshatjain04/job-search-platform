package io.myjobai.runtime;

import org.springframework.core.env.Environment;
import java.net.URI;
import java.util.*;

public final class PlatformSettings {
    private final Environment env;
    public PlatformSettings(Environment env){this.env=env;validate();}
    public String get(String key,String fallback){return env.getProperty(key,fallback);}
    public String required(String key){String value=get(key,"");if(value.isBlank())throw new IllegalArgumentException("Missing required configuration: "+key);return value;}
    public boolean test(){return get("APP_MODE","production").equals("test");}
    public String role(){return get("APP_ROLE","api");}
    public String publicUrl(){return get("APP_PUBLIC_URL","http://localhost:8080").replaceAll("/+$","");}
    public boolean secureCookies(){return publicUrl().startsWith("https://");}
    public Map<String,String> aiEnvironment(){var map=new HashMap<String,String>();for(String key:List.of("LLM_PROVIDER","LLM_FALLBACK_PROVIDERS","LLM_TIMEOUT_SECONDS","LLM_MAX_ATTEMPTS","LLM_MAX_OUTPUT_TOKENS")){String v=get(key,"");if(!v.isBlank())map.put(key,v);}for(String provider:List.of("GEMINI","OPENAI","CLAUDE")){for(String suffix:List.of("MODEL_CHEAP","MODEL_MEDIUM","MODEL_HIGH","INPUT_COST_PER_MILLION","OUTPUT_COST_PER_MILLION")){String key=provider+"_"+suffix;map.put(key,get(key,""));}String credential=provider.equals("CLAUDE")?"ANTHROPIC_API_KEY":provider+"_API_KEY";map.put(credential,get(credential,""));}map.entrySet().removeIf(e->e.getValue().isBlank());return map;}
    private void validate(){
        if(!Set.of("production","test").contains(get("APP_MODE","production")))throw new IllegalArgumentException("APP_MODE must be production or test");
        if(!Set.of("api","mcp","ingestion","ai","communication").contains(role()))throw new IllegalArgumentException("Invalid APP_ROLE");
        required("ENCRYPTION_MASTER_KEY");required("SUPABASE_DB_URL");required("SUPABASE_DB_USER");required("SUPABASE_DB_PASSWORD");
        var uri=URI.create(publicUrl());if(test()){if(!Set.of("localhost","127.0.0.1").contains(uri.getHost()))throw new IllegalArgumentException("Test mode requires a loopback APP_PUBLIC_URL");}
        else{
            if(!uri.getScheme().equals("https"))throw new IllegalArgumentException("Production APP_PUBLIC_URL must use HTTPS");
            if(get("SUPABASE_DB_URL","").matches("(?i).*//(localhost|127\\.0\\.0\\.1|postgres|db)[:/].*"))throw new IllegalArgumentException("Production PostgreSQL must be remote; use Supabase");
            if(!get("SUPABASE_DB_URL","").contains("sslmode=verify-full"))throw new IllegalArgumentException("Production SUPABASE_DB_URL must include sslmode=verify-full");
            required("SUPABASE_URL");required("SUPABASE_ANON_KEY");required("SUPABASE_SERVICE_ROLE_KEY");required("STORAGE_BUCKET");
            if(get("AI_MODE","live").equals("test")||get("MAIL_MODE","live").equals("test"))throw new IllegalArgumentException("Test providers are prohibited in production");
        }
    }
}
