package io.myjobai.mcp;

import io.myjobai.application.*;
import io.myjobai.ai.JsonSchemaValidator;
import io.myjobai.domain.*;
import io.myjobai.runtime.IdentityService.Principal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.function.BiFunction;

/** Stateless Streamable HTTP transport: tool handlers only validate/translate and call shared services. */
@RestController
public class McpController {
    private record Tool(String name,String description,Map<String,Object> schema,boolean readOnly,BiFunction<UUID,Map<String,Object>,Object> call){}
    private final Map<String,Tool> tools=new LinkedHashMap<>();private final JsonMapper json;
    public McpController(JobService jobs,ResumeService resumes,RecruiterService recruiters,GenerationService generation,ApplicationService applications,JsonMapper json){this.json=json;
        add("jobs.search","Search this user's canonical opportunities",Map.of("role",string()),List.of(),true,(u,a)->jobs.search(u,new Opportunity.Search((String)a.get("role"),null,null,null,null,null,null,null,null,null,null,0,25)));
        add("jobs.get","Read a canonical opportunity",Map.of("jobId",uuid()),List.of("jobId"),true,(u,a)->jobs.get(u,id(a,"jobId")));
        add("jobs.analyze","Calculate structured job match and gaps",Map.of("jobId",uuid()),List.of("jobId"),false,(u,a)->jobs.analyze(u,id(a,"jobId")));
        add("resume.tailor","Queue a resume grounded in verified candidate facts",Map.of("resumeId",uuid(),"jobId",uuid(),"idempotencyKey",string()),List.of("resumeId","jobId","idempotencyKey"),false,(u,a)->resumes.tailor(u,id(a,"resumeId"),id(a,"jobId"),(String)a.get("idempotencyKey")));
        add("resume.evaluate","Read internal compatibility and parsing score history",Map.of("versionId",uuid()),List.of("versionId"),true,(u,a)->resumes.version(u,id(a,"versionId")).scoringHistory());
        add("resume.render","Get authenticated download paths for an immutable rendered version",Map.of("versionId",uuid()),List.of("versionId"),true,(u,a)->{var v=resumes.version(u,id(a,"versionId"));return Map.of("versionId",v.id(),"pdf","/api/v1/resumes/versions/"+v.id()+"/pdf","docx","/api/v1/resumes/versions/"+v.id()+"/docx");});
        add("recruiter.find","Queue bounded public-source recruiter research for an eligible job",Map.of("jobId",uuid(),"idempotencyKey",string()),List.of("jobId","idempotencyKey"),false,(u,a)->recruiters.request(u,id(a,"jobId"),(String)a.get("idempotencyKey")));
        for(var channel:Outreach.Channel.values())add("outreach.generate_"+channel.name().toLowerCase(Locale.ROOT),"Generate a grounded "+channel+" draft; does not approve or send",Map.of("jobId",uuid(),"recipientId",uuid(),"resumeVersionId",uuid(),"instructions",string(),"idempotencyKey",string()),List.of("jobId","idempotencyKey"),false,(u,a)->generation.requestOutreach(u,id(a,"jobId"),channel,optionalId(a,"recipientId"),optionalId(a,"resumeVersionId"),(String)a.getOrDefault("instructions",""),"normal",(String)a.get("idempotencyKey")));
        add("applications.create","Track a saved opportunity",Map.of("jobId",uuid()),List.of("jobId"),false,(u,a)->applications.create(u,id(a,"jobId")));
        add("applications.update","Perform a legal lifecycle transition",Map.of("applicationId",uuid(),"state",Map.of("type","string","enum",Arrays.stream(ApplicationLifecycle.State.values()).map(Enum::name).toList()),"note",string()),List.of("applicationId","state"),false,(u,a)->applications.update(u,id(a,"applicationId"),ApplicationLifecycle.State.valueOf((String)a.get("state")),(String)a.getOrDefault("note","")));
        add("applications.list","List this user's application tracker",Map.of(),List.of(),true,(u,a)->applications.list(u));
    }
    private void add(String name,String description,Map<String,Object> props,List<String> required,boolean readOnly,BiFunction<UUID,Map<String,Object>,Object> call){tools.put(name,new Tool(name,description,Map.of("type","object","properties",props,"required",required,"additionalProperties",false),readOnly,call));}
    private static Map<String,Object> string(){return Map.of("type","string","maxLength",1500);}private static Map<String,Object> uuid(){return Map.of("type","string","format","uuid");}private static UUID id(Map<String,Object> a,String key){return UUID.fromString((String)a.get(key));}private static UUID optionalId(Map<String,Object> a,String key){return a.containsKey(key)?id(a,key):null;}
    @PostMapping(value="/mcp",produces=MediaType.APPLICATION_JSON_VALUE) ResponseEntity<?> handle(@AuthenticationPrincipal Principal p,@RequestBody Map<String,Object> input){
        Object id=input.get("id");String method=Objects.toString(input.get("method"),"");if(id==null&&method.startsWith("notifications/"))return ResponseEntity.accepted().build();
        try{Object result=switch(method){case "initialize"->Map.of("protocolVersion","2025-06-18","capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","myjobai","version","0.1.0"));case "ping"->Map.of();case "tools/list"->Map.of("tools",tools.values().stream().map(t->Map.of("name",t.name(),"description",t.description(),"inputSchema",t.schema(),"annotations",Map.of("readOnlyHint",t.readOnly(),"destructiveHint",false,"openWorldHint",!t.readOnly()))).toList());case "tools/call"->call(p.id(),input);default->throw new IllegalArgumentException("Unknown JSON-RPC method");};return ResponseEntity.ok(Map.of("jsonrpc","2.0","id",id==null?0:id,"result",result));}
        catch(RuntimeException e){return ResponseEntity.ok(Map.of("jsonrpc","2.0","id",id==null?0:id,"error",Map.of("code",-32602,"message","Invalid method, parameters or tool request")));}
    }
    @SuppressWarnings("unchecked") private Object call(UUID user,Map<String,Object> input){var params=(Map<String,Object>)input.getOrDefault("params",Map.of());var tool=tools.get(params.get("name"));if(tool==null)throw new IllegalArgumentException();var args=(Map<String,Object>)params.getOrDefault("arguments",Map.of());JsonSchemaValidator.validate(tool.schema(),args);try{Object output=tool.call().apply(user,args);return Map.of("content",List.of(Map.of("type","text","text",json.writeValueAsString(output))),"structuredContent",Map.of("result",output),"isError",false);}catch(DomainException e){return Map.of("content",List.of(Map.of("type","text","text",e.code()+": "+e.getMessage())),"isError",true);}}
}
