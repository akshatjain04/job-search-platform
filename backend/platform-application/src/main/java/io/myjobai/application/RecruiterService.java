package io.myjobai.application;

import io.myjobai.domain.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

public class RecruiterService {
    private final Ports.Contacts contacts;private final Ports.RecruiterResearch research;private final Ports.Outbox outbox;private final Ports.Audit audit;private final JobService jobs;private final Clock clock;
    public RecruiterService(Ports.Contacts contacts,Ports.RecruiterResearch research,Ports.Outbox outbox,Ports.Audit audit,JobService jobs,Clock clock){this.contacts=contacts;this.research=research;this.outbox=outbox;this.audit=audit;this.jobs=jobs;this.clock=clock;}
    public List<Contact> list(UUID user,UUID job){jobs.get(user,job);return contacts.forJob(user,job);}
    @Transactional public Contact add(UUID user,UUID job,String name,String value,Contact.Type type,String source,boolean explicitlyVerified){var j=jobs.get(user,job);return store(user,j,new Ports.ContactEvidence(name,value,type,source),explicitlyVerified?Contact.Verification.USER_VERIFIED:Contact.Verification.UNVERIFIED,"User-provided contact; verification asserted explicitly by user");}
    private Contact store(UUID user,Opportunity.Job job,Ports.ContactEvidence evidence,Contact.Verification verification,String method){var recruiter=new Contact.Recruiter(UUID.randomUUID(),user,evidence.label(),job.company(),evidence.sourceUrl(),clock.instant());var contact=new Contact(UUID.randomUUID(),user,recruiter.id(),evidence.label(),evidence.value(),evidence.type(),evidence.sourceUrl(),verification==Contact.Verification.PUBLIC?"PUBLIC_PAGE":"MANUAL",verification==Contact.Verification.PUBLIC?.9:explicitConfidence(verification),verification,clock.instant(),method);contacts.save(recruiter,contact,job.id());return contact;}
    private double explicitConfidence(Contact.Verification status){return status==Contact.Verification.USER_VERIFIED?1:.25;}
    @Transactional public AsyncJob request(UUID user,UUID job,String key){var match=jobs.analyze(user,job);if(!match.eligible()||match.score()<40)throw DomainException.invalid("Research requires an eligible job with match score at least 40");return outbox.enqueue(user,"RESEARCH_RECRUITER",Map.of("jobId",job.toString()),Checks.text(key,"Idempotency-Key",100),clock.instant());}
    @Transactional public List<Contact> research(AsyncJob task){var job=jobs.get(task.userId(),UUID.fromString(task.payload().get("jobId")));var previous=contacts.forJob(task.userId(),job.id());if(previous.stream().anyMatch(c->c.verificationStatus()==Contact.Verification.PUBLIC))return previous;var found=research.find(job);for(var evidence:found)store(task.userId(),job,evidence,Contact.Verification.PUBLIC,"Explicit link on fetched public page; identity label is source text, not an AI assertion");audit.record(task.userId(),"RECRUITER_RESEARCHED","job",job.id(),Map.of("contactCount",Integer.toString(found.size())));return contacts.forJob(task.userId(),job.id());}
}
