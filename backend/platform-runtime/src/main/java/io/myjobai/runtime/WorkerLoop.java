package io.myjobai.runtime;

import io.myjobai.application.*;
import io.myjobai.domain.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.*;
import java.util.*;

public class WorkerLoop {
    private static final Logger log=LoggerFactory.getLogger(WorkerLoop.class);
    private final PlatformSettings settings;private final Ports.Outbox outbox;private final JobService jobs;private final GenerationService generation;private final DiscoveryService discovery;private final RecruiterService research;private final CommunicationService communication;private final Ports.ObjectStorage storage;private final MailboxOAuth mailboxes;private final Map<String,Ports.MailboxProvider> providers;private final Clock clock;
    public WorkerLoop(PlatformSettings settings,Ports.Outbox outbox,JobService jobs,GenerationService generation,DiscoveryService discovery,RecruiterService research,CommunicationService communication,Ports.ObjectStorage storage,MailboxOAuth mailboxes,List<Ports.MailboxProvider> providers,Clock clock){this.settings=settings;this.outbox=outbox;this.jobs=jobs;this.generation=generation;this.discovery=discovery;this.research=research;this.communication=communication;this.storage=storage;this.mailboxes=mailboxes;var registry=new HashMap<String,Ports.MailboxProvider>();providers.forEach(p->registry.put(p.key(),p));this.providers=Map.copyOf(registry);this.clock=clock;}
    @Scheduled(fixedDelayString="${WORKER_POLL_MS:2000}") public void poll(){
        Set<String> types=switch(settings.role()){case "ingestion"->Set.of("DISCOVER_JOBS","MATCH_JOB");case "ai"->Set.of("TAILOR_RESUME","GENERATE_OUTREACH","RESEARCH_RECRUITER");case "communication"->Set.of("SEND_EMAIL");default->Set.of();};if(types.isEmpty())return;
        var claimed=outbox.claim(types,UUID.randomUUID().toString(),clock.instant(),Duration.ofMinutes(10));if(claimed.isEmpty())return;var task=claimed.get();MDC.put("jobId",task.id().toString());MDC.put("userId",task.userId().toString());
        try{switch(task.eventType()){case "MATCH_JOB"->jobs.analyze(task.userId(),UUID.fromString(task.payload().get("jobId")));case "DISCOVER_JOBS"->discovery.discover(task);case "TAILOR_RESUME"->generation.tailor(task);case "GENERATE_OUTREACH"->generation.outreach(task);case "RESEARCH_RECRUITER"->research.research(task);case "SEND_EMAIL"->send(task);default->throw DomainException.invalid("Unsupported worker task");}outbox.complete(task,clock.instant());log.info("Async task completed: {}",task.eventType());}
        catch(IntegrationException e){outbox.fail(task,e.code(),e.retryable()&&!e.ambiguous(),clock.instant());log.warn("Async integration failed: {}",e.code());}
        catch(DomainException e){outbox.fail(task,e.code()+": "+e.getMessage(),false,clock.instant());log.warn("Async task rejected: {}",e.code());}
        catch(RuntimeException e){outbox.fail(task,"INTERNAL_WORKER_ERROR",false,clock.instant());log.error("Async task failed with {}",e.getClass().getSimpleName());}
        finally{MDC.clear();}
    }
    @Scheduled(fixedDelay=60000) public void schedule(){if(settings.role().equals("ingestion"))discovery.schedule();}
    private void send(AsyncJob task){
        var reservation=communication.reserve(task.userId(),UUID.fromString(task.payload().get("approvalId")));if(reservation.alreadySent())return;if(reservation.uncertain())throw new IntegrationException("MAIL_DELIVERY_UNKNOWN","Previous dispatch requires manual sent-mail reconciliation",false,true);
        try{
            byte[] attachment=storage.get(task.userId(),reservation.resume().pdfKey());if(!GenerationService.hashBytes(attachment).equals(reservation.version().attachmentHash()))throw new IntegrationException("ATTACHMENT_CHANGED","Stored attachment differs from approved bytes",false,false);
            Ports.Mailbox box=settings.test()?new Ports.Mailbox(task.userId(),"test","demo@example.com","","",clock.instant().plusSeconds(3600)):mailboxes.ready(task.userId());
            if(!settings.test()&&box.provider().equals("test"))throw new IntegrationException("MAIL_CONFIG","Test mailboxes are prohibited in production",false,false);
            if(box.expiresAt().isBefore(clock.instant()))throw new IntegrationException("MAIL_TOKEN_EXPIRED","Mailbox token expired; reconnect the mailbox",false,false);
            var provider=providers.get(box.provider());if(provider==null)throw new IntegrationException("MAIL_CONFIG","Mailbox provider is unavailable",false,false);
            var result=provider.send(new Ports.ApprovedMail(reservation.approval(),reservation.version(),attachment,box.accessToken(),box.address()));communication.sent(reservation,result);
        }catch(IntegrationException e){communication.failed(reservation,e);throw e;}
    }
}
