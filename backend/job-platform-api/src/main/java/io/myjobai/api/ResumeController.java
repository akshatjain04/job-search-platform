package io.myjobai.api;

import io.myjobai.application.ResumeService;
import io.myjobai.domain.*;
import io.myjobai.runtime.IdentityService.Principal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {
    private final ResumeService resumes;
    public ResumeController(ResumeService resumes){this.resumes=resumes;}
    @GetMapping List<Resume.Base> bases(@AuthenticationPrincipal Principal p){return resumes.bases(p.id());}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) Resume.Base upload(@AuthenticationPrincipal Principal p,@RequestPart("file") MultipartFile file)throws IOException{return resumes.upload(p.id(),file.getOriginalFilename(),file.getContentType(),file.getBytes());}
    public record TailorInput(UUID resumeId,UUID jobId){}
    @PostMapping("/tailor") ResponseEntity<AsyncJob> tailor(@AuthenticationPrincipal Principal p,@RequestBody TailorInput input,@RequestHeader("Idempotency-Key") String key){return ResponseEntity.accepted().body(resumes.tailor(p.id(),input.resumeId(),input.jobId(),key));}
    @GetMapping("/versions") List<Resume.Version> versions(@AuthenticationPrincipal Principal p,@RequestParam(required=false) UUID jobId){return resumes.versions(p.id(),jobId);}
    @GetMapping("/versions/{id}") Resume.Version version(@AuthenticationPrincipal Principal p,@PathVariable UUID id){return resumes.version(p.id(),id);}
    @GetMapping("/versions/{id}/{format}") ResponseEntity<byte[]> download(@AuthenticationPrincipal Principal p,@PathVariable UUID id,@PathVariable String format){return ResponseEntity.ok().contentType(MediaType.parseMediaType(format.equals("pdf")?"application/pdf":"application/vnd.openxmlformats-officedocument.wordprocessingml.document")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=resume-"+id+"."+format).header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(resumes.download(p.id(),id,format));}
}
