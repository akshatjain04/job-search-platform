package io.myjobai.api;

import io.myjobai.application.*;
import io.myjobai.domain.*;
import io.myjobai.runtime.IdentityService.Principal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class JobsController {
    private final JobService jobs;private final CaptureService captures;private final RecruiterService recruiters;
    public JobsController(JobService jobs,CaptureService captures,RecruiterService recruiters){this.jobs=jobs;this.captures=captures;this.recruiters=recruiters;}
    @GetMapping("/jobs") Opportunity.Page<Opportunity.Job> search(@AuthenticationPrincipal Principal p,@RequestParam(required=false) String role,@RequestParam(required=false) String company,@RequestParam(required=false) String location,@RequestParam(required=false) Integer salaryMin,@RequestParam(required=false) Integer experienceMax,@RequestParam(required=false) Boolean remote,@RequestParam(required=false) Instant since,@RequestParam(required=false) String source,@RequestParam(required=false) Boolean referral,@RequestParam(required=false) Boolean hasRecruiter,@RequestParam(required=false) Double minScore,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return jobs.search(p.id(),new Opportunity.Search(role,company,location,salaryMin,experienceMax,remote,since,source,referral,hasRecruiter,minScore,page,size));}
    @GetMapping("/jobs/{id}") Opportunity.Job get(@AuthenticationPrincipal Principal p,@PathVariable UUID id){return jobs.get(p.id(),id);}
    @GetMapping("/jobs/{id}/sources") List<Opportunity.Source> sources(@AuthenticationPrincipal Principal p,@PathVariable UUID id){return jobs.sources(p.id(),id);}
    @PostMapping("/jobs/import") Opportunity.Job ingest(@AuthenticationPrincipal Principal p,@RequestBody Ports.DiscoveredJob input){return jobs.ingest(p.id(),input,"manual-import");}
    @PostMapping("/page-captures") Opportunity.Job capture(@AuthenticationPrincipal Principal p,@RequestBody Ports.CaptureInput input){return captures.capture(p.id(),input);}
    @PostMapping("/jobs/{id}/analyze") Opportunity.Match analyze(@AuthenticationPrincipal Principal p,@PathVariable UUID id){return jobs.analyze(p.id(),id);}
    @GetMapping("/jobs/{id}/match") Opportunity.Match match(@AuthenticationPrincipal Principal p,@PathVariable UUID id){return jobs.match(p.id(),id);}
    @GetMapping("/jobs/{id}/recruiters") List<Contact> recruiters(@AuthenticationPrincipal Principal p,@PathVariable UUID id){return recruiters.list(p.id(),id);}
    public record ContactInput(String name,String value,Contact.Type type,String sourceUrl,boolean explicitlyVerified){}
    @PostMapping("/jobs/{id}/recruiters") Contact contact(@AuthenticationPrincipal Principal p,@PathVariable UUID id,@RequestBody ContactInput input){return recruiters.add(p.id(),id,input.name(),input.value(),input.type(),input.sourceUrl(),input.explicitlyVerified());}
    @PostMapping("/jobs/{id}/recruiters/research") AsyncJob research(@AuthenticationPrincipal Principal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){return recruiters.request(p.id(),id,key);}
}
