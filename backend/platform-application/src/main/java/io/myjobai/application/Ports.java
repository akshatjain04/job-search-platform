package io.myjobai.application;

import io.myjobai.domain.*;
import java.time.*;
import java.util.*;

/** Outbound contracts; implementations own transport and persistence details. */
public final class Ports {
    private Ports() {}
    public interface Profiles {
        Optional<Candidate.Profile> find(UUID user);
        void save(Candidate.Profile profile);
        List<Candidate.Fact> facts(UUID user);
        void saveFact(Candidate.Fact fact);
    }
    public interface Jobs {
        Opportunity.Job canonicalize(Opportunity.Job job, Opportunity.Source source);
        Optional<Opportunity.Job> find(UUID user,UUID id);
        Opportunity.Page<Opportunity.Job> search(UUID user,Opportunity.Search query);
        List<Opportunity.Source> sources(UUID user,UUID job);
        void saveMatch(Opportunity.Match match);
        Optional<Opportunity.Match> match(UUID user,UUID job);
    }
    public interface Resumes {
        void saveBase(Resume.Base base);
        Optional<Resume.Base> base(UUID user,UUID id);
        List<Resume.Base> bases(UUID user);
        void saveVersion(Resume.Version version);
        Optional<Resume.Version> version(UUID user,UUID id);
        List<Resume.Version> versions(UUID user,UUID job);
    }
    public interface Applications {
        ApplicationLifecycle.Application create(UUID user,UUID job,Instant now);
        Optional<ApplicationLifecycle.Application> find(UUID user,UUID id,boolean lock);
        Optional<ApplicationLifecycle.Application> forJob(UUID user,UUID job);
        List<ApplicationLifecycle.Application> list(UUID user);
        void transition(ApplicationLifecycle.Application current,ApplicationLifecycle.State next,String note,Instant now);
        List<ApplicationLifecycle.Event> events(UUID user,UUID application);
    }
    public interface Contacts {
        void save(Contact.Recruiter recruiter,Contact contact,UUID job);
        Optional<Contact> find(UUID user,UUID id);
        List<Contact> forJob(UUID user,UUID job);
    }
    public interface Messages {
        void create(Outreach.Message message,Outreach.Version version);
        Optional<Outreach.Message> find(UUID user,UUID id,boolean lock);
        Optional<Outreach.Version> version(UUID user,UUID id);
        List<Outreach.Message> list(UUID user);
        List<Outreach.Version> versions(UUID user,UUID message);
        void revise(Outreach.Message message,Outreach.Version version,Instant now);
        void state(UUID user,UUID message,Outreach.State state,Instant now);
        void approve(Outreach.Approval approval);
        Optional<Outreach.Approval> approval(UUID user,UUID id);
        Optional<Outreach.Approval> currentApproval(UUID user,UUID message);
        void invalidate(UUID user,UUID message,Instant now);
    }
    public interface Outbox {
        AsyncJob enqueue(UUID user,String type,Map<String,String> payload,String idempotencyKey,Instant now);
        Optional<AsyncJob> claim(Set<String> types,String claimToken,Instant now,Duration lease);
        void complete(AsyncJob job,Instant now);
        void fail(AsyncJob job,String safeError,boolean retryable,Instant now);
        List<AsyncJob> list(UUID user);
        Optional<AsyncJob> find(UUID user,UUID id);
        void retry(UUID user,UUID id,Instant now);
    }
    public interface Audit { void record(UUID user,String action,String resourceType,UUID resource,Map<String,String> metadata); }
    public interface ObjectStorage {
        String put(UUID user,String key,byte[] bytes,String contentType);
        byte[] get(UUID user,String key);
        void delete(UUID user,String key);
    }
    public interface ResumeParser { String extract(byte[] bytes,String mediaType); }
    public record Rendered(byte[] pdf,byte[] docx,String pdfText,String docxText) {}
    public interface ResumeRenderer { Rendered render(Resume.Structured resume,String template); }
    public enum Tier { CHEAP, MEDIUM, HIGH }
    public record LlmRequest(String task,Tier tier,String system,Map<String,Object> untrustedData,Map<String,Object> schema) {}
    public record Usage(String provider,String model,long inputTokens,long outputTokens,long latencyMs,Double estimatedCost) {}
    public record Completion(Map<String,Object> output,Usage usage) {}
    public interface LlmProvider { String key(); Completion generate(LlmRequest request); }
    public interface Intelligence { Completion generate(UUID user,LlmRequest request); }
    public record SearchResult(String title,String url,String snippet) {}
    public interface WebResearch { List<SearchResult> search(String query); String publicPage(String url); }
    public record CaptureInput(String url,String title,String text,String company,Map<String,Object> metadata) {}
    public interface PageCaptures { DiscoveredJob extract(CaptureInput input); }
    public record ContactEvidence(String label,String value,Contact.Type type,String sourceUrl) {}
    public interface RecruiterResearch { List<ContactEvidence> find(Opportunity.Job job); }
    public record SourceCapabilities(boolean discovery,boolean jobDetails,boolean recruiter,String accessMethod) {}
    public record DiscoveryCriteria(String board,String role,int limit) {}
    public record DiscoveredJob(String externalId,String title,String company,String location,String description,
                                Opportunity.Kind kind,boolean remote,String employmentType,Integer salaryMin,Integer salaryMax,
                                String currency,Integer experienceYears,List<String> skills,Instant postedAt,String url) {}
    public interface JobSourceConnector {
        String key(); List<DiscoveredJob> discoverJobs(DiscoveryCriteria criteria);
        DiscoveredJob fetchJob(String board,String externalId);
        SourceCapabilities capabilities();
    }
    public record ApprovedMail(Outreach.Approval approval,Outreach.Version version,byte[] attachment,String mailboxAccessToken,String mailboxAddress) {}
    public record SendResult(String providerId,boolean testMode) {}
    public interface MailboxProvider { String key(); SendResult send(ApprovedMail message); }
    public record Mailbox(UUID userId,String provider,String address,String accessToken,String refreshToken,Instant expiresAt) {}
    public interface Mailboxes { Optional<Mailbox> find(UUID user); void save(Mailbox mailbox); }
    public record ConnectorConfig(UUID id,UUID userId,String connector,String board,String role,int intervalMinutes,boolean enabled,Instant nextRunAt) {}
    public interface Connectors {
        List<ConnectorConfig> list(UUID user); void save(ConnectorConfig config);
        List<ConnectorConfig> due(Instant now); void schedule(UUID id,Instant nextRun);
        void run(UUID config,UUID user,String status,int discovered,String error,Instant now);
    }
}
