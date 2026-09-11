package io.myjobai.application;

import io.myjobai.domain.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

public class ApprovalService {
    private final Ports.Messages messages;private final Ports.Resumes resumes;private final Ports.Contacts contacts;private final Ports.Outbox outbox;private final Ports.Audit audit;private final Clock clock;
    public ApprovalService(Ports.Messages messages,Ports.Resumes resumes,Ports.Contacts contacts,Ports.Outbox outbox,Ports.Audit audit,Clock clock){this.messages=messages;this.resumes=resumes;this.contacts=contacts;this.outbox=outbox;this.audit=audit;this.clock=clock;}
    public record Preview(Outreach.Message message,Outreach.Version version,Contact recipient,Resume.Version resume,String fingerprint,UUID approvalId) {}
    public Preview preview(UUID user,UUID id){return previewOwned(messages.find(user,id,false).orElseThrow(DomainException::missing));}
    private Preview previewOwned(Outreach.Message m){
        var v=messages.version(m.userId(),m.currentVersionId()).orElseThrow(DomainException::missing);
        var c=v.recipientId()==null?null:contacts.find(m.userId(),v.recipientId()).orElseThrow(DomainException::missing);
        var r=v.resumeVersionId()==null?null:resumes.version(m.userId(),v.resumeVersionId()).orElseThrow(DomainException::missing);
        return new Preview(m,v,c,r,v.fingerprint(),messages.currentApproval(m.userId(),m.id()).map(Outreach.Approval::id).orElse(null));
    }
    public List<Outreach.Message> list(UUID user){return messages.list(user);}
    public List<Outreach.Version> versions(UUID user,UUID id){messages.find(user,id,false).orElseThrow(DomainException::missing);return messages.versions(user,id);}
    @Transactional public Preview revise(UUID user,UUID id,UUID recipient,String subject,String body,UUID resumeVersion){
        var m=messages.find(user,id,true).orElseThrow(DomainException::missing);
        if(Set.of(Outreach.State.SENDING,Outreach.State.SENT,Outreach.State.DELIVERY_UNKNOWN).contains(m.state()))throw DomainException.conflict("A dispatched message cannot be edited. Create a new draft.");
        Contact c=recipient==null?null:contacts.find(user,recipient).orElseThrow(DomainException::missing);
        Resume.Version r=resumeVersion==null?null:resumes.version(user,resumeVersion).orElseThrow(DomainException::missing);
        if(r!=null&&!r.jobId().equals(m.jobId()))throw DomainException.invalid("Attachment belongs to a different job");
        var v=new Outreach.Version(UUID.randomUUID(),user,m.id(),recipient,c==null?"":c.value(),subject,body,resumeVersion,r==null?"":r.pdfHash(),clock.instant());
        messages.revise(m,v,clock.instant());audit.record(user,"APPROVAL_INVALIDATED","outreach",id,Map.of("newVersion",v.id().toString()));return preview(user,id);
    }
    @Transactional public Preview requestApproval(UUID user,UUID id){
        var m=messages.find(user,id,true).orElseThrow(DomainException::missing);
        if(m.state()!=Outreach.State.DRAFT)throw DomainException.conflict("Only drafts can request approval");
        var p=previewOwned(m);requireSendable(p);messages.state(user,id,Outreach.State.AWAITING_APPROVAL,clock.instant());return preview(user,id);
    }
    @Transactional public Outreach.Approval approve(UUID user,UUID id,UUID expectedVersion,String expectedFingerprint,boolean explicitlyApproved){
        var m=messages.find(user,id,true).orElseThrow(DomainException::missing);
        if(m.state()!=Outreach.State.AWAITING_APPROVAL||!explicitlyApproved)throw DomainException.conflict("Explicit approval of a pending preview is required");
        var p=previewOwned(m);requireSendable(p);
        if(!p.version().id().equals(expectedVersion)||!p.fingerprint().equals(expectedFingerprint))throw DomainException.conflict("Preview changed; review the current email and attachment");
        var approval=new Outreach.Approval(UUID.randomUUID(),user,id,p.version().id(),p.resume().id(),p.recipient().id(),p.fingerprint(),clock.instant(),null);
        messages.approve(approval);messages.state(user,id,Outreach.State.APPROVED,clock.instant());audit.record(user,"EMAIL_APPROVED","approval",approval.id(),Map.of("version",p.version().id().toString()));return approval;
    }
    @Transactional public AsyncJob queue(UUID user,UUID approvalId){
        var approval=messages.approval(user,approvalId).orElseThrow(DomainException::missing);
        var m=messages.find(user,approval.messageId(),true).orElseThrow(DomainException::missing);
        var p=previewOwned(m);requireSendable(p);approval.requireValid(m,p.version(),p.recipient(),p.resume());
        if(!Set.of(Outreach.State.APPROVED,Outreach.State.QUEUED).contains(m.state()))throw DomainException.conflict("Message must have a current unsent approval");
        var task=outbox.enqueue(user,"SEND_EMAIL",Map.of("approvalId",approvalId.toString()),approvalId.toString(),clock.instant());
        messages.state(user,m.id(),Outreach.State.QUEUED,clock.instant());audit.record(user,"EMAIL_QUEUED","approval",approvalId,Map.of("task",task.id().toString()));return task;
    }
    @Transactional public void reject(UUID user,UUID id){var m=messages.find(user,id,true).orElseThrow(DomainException::missing);if(!Set.of(Outreach.State.AWAITING_APPROVAL,Outreach.State.APPROVED,Outreach.State.QUEUED).contains(m.state()))throw DomainException.conflict("Message is not pending approval or dispatch");messages.invalidate(user,id,clock.instant());messages.state(user,id,Outreach.State.REJECTED,clock.instant());audit.record(user,"EMAIL_REJECTED","outreach",id,Map.of());}
    private void requireSendable(Preview p){
        if(p.message().channel()!=Outreach.Channel.EMAIL||p.recipient()==null||!p.recipient().sendable()||p.resume()==null||p.version().subject().isBlank())throw DomainException.invalid("Email requires a verified recipient, subject and exact resume attachment");
        if(!p.resume().jobId().equals(p.message().jobId()))throw DomainException.invalid("Resume must target this job");
        if(!p.version().attachmentHash().equals(p.resume().pdfHash()))throw DomainException.conflict("Attachment fingerprint changed");
    }
}
