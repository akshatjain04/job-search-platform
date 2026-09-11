package io.myjobai.runtime;

import io.myjobai.ai.*;
import io.myjobai.application.*;
import io.myjobai.connectors.*;
import io.myjobai.domain.*;
import io.myjobai.mail.*;
import io.myjobai.persistence.*;
import io.myjobai.resume.*;
import io.myjobai.storage.*;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling
@EnableTransactionManagement
@Import({
  SecurityConfiguration.class,
  AuthController.class,
  MailboxController.class,
  ApiErrors.class,
  WorkerLoop.class
})
public class RuntimeConfiguration {
  @Bean
  PlatformSettings settings(Environment env) {
    return new PlatformSettings(env);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  JsonRows rows(JdbcTemplate jdbc, JsonMapper json) {
    return new JsonRows(jdbc, json);
  }

  @Bean
  TokenCipher cipher(PlatformSettings s) {
    return new TokenCipher(s.required("ENCRYPTION_MASTER_KEY"));
  }

  @Bean
  IdentityService identity(JsonRows r, TokenCipher c, PlatformSettings s, Clock clock) {
    return new IdentityService(r, c, s, clock);
  }

  @Bean
  Ports.Profiles profiles(JsonRows r) {
    return new JdbcProfiles(r);
  }

  @Bean
  Ports.Jobs jobs(JsonRows r) {
    return new JdbcJobs(r);
  }

  @Bean
  Ports.Resumes resumes(JsonRows r) {
    return new JdbcResumes(r);
  }

  @Bean
  Ports.Applications applications(JsonRows r) {
    return new JdbcApplications(r);
  }

  @Bean
  Ports.Contacts contacts(JsonRows r) {
    return new JdbcContacts(r);
  }

  @Bean
  Ports.Messages messages(JsonRows r) {
    return new JdbcMessages(r);
  }

  @Bean
  Ports.Outbox outbox(JsonRows r) {
    return new JdbcOutbox(r);
  }

  @Bean
  Ports.Audit audit(JsonRows r) {
    return new JdbcAudit(r);
  }

  @Bean
  Ports.Analytics analytics(JsonRows r) {
    return new JdbcAnalytics(r);
  }

  @Bean
  InsightsService insights(Ports.Analytics a) {
    return new InsightsService(a);
  }

  @Bean
  Ports.Connectors connectors(JsonRows r) {
    return new JdbcConnectors(r);
  }

  @Bean
  Ports.Mailboxes mailboxes(JsonRows r, TokenCipher c) {
    return new JdbcMailboxes(r, c);
  }

  @Bean
  MailboxOAuth mailboxOAuth(
      JsonRows r,
      Ports.Mailboxes boxes,
      TokenCipher c,
      PlatformSettings s,
      IdentityService identity,
      Clock clock) {
    return new MailboxOAuth(r, boxes, c, s, identity, clock);
  }

  @Bean
  SafeWebClient publicHttp() {
    return new SafeWebClient();
  }

  @Bean
  PageExtraction extraction(JsonMapper json, Clock clock) {
    return new PageExtraction(json, clock);
  }

  @Bean
  GreenhouseConnector greenhouse(SafeWebClient http, JsonMapper json, Clock clock) {
    return new GreenhouseConnector(http::get, json, clock);
  }

  @Bean
  LeverConnector lever(SafeWebClient http, JsonMapper json, Clock clock) {
    return new LeverConnector(http::get, json, clock);
  }

  @Bean
  AshbyConnector ashby(SafeWebClient http, JsonMapper json, Clock clock) {
    return new AshbyConnector(http::get, json, clock);
  }

  @Bean
  CompanyCareerConnector career(SafeWebClient http, PageExtraction extraction) {
    return new CompanyCareerConnector(http, extraction);
  }

  @Bean
  Ports.WebResearch webResearch(SafeWebClient http, JsonMapper json, PlatformSettings settings) {
    return new BraveWebResearch(http, json, settings.get("BRAVE_SEARCH_API_KEY", ""));
  }

  @Bean
  Ports.RecruiterResearch research(Ports.WebResearch research) {
    return new PublicRecruiterResearch(research);
  }

  @Bean
  Ports.LlmProvider llm(PlatformSettings settings, JsonMapper json) {
    if (settings.test()) return new DeterministicTestProvider();
    var http = new ProviderHttp(json);
    return new LlmProviderRegistry(
            Map.of(
                "gemini",
                s -> new GeminiLlmProvider(s, http, json),
                "openai",
                s -> new OpenAiLlmProvider(s, http, json),
                "claude",
                s -> new ClaudeLlmProvider(s, http, json)))
        .select(settings.aiEnvironment());
  }

  @Bean
  Ports.Intelligence intelligence(
      JsonRows rows,
      Ports.LlmProvider llm,
      PlatformSettings s,
      org.springframework.transaction.PlatformTransactionManager transactions) {
    String routing =
        llm.key()
            + s.aiEnvironment().entrySet().stream()
                .filter(e -> e.getKey().contains("MODEL_"))
                .sorted(Map.Entry.comparingByKey())
                .toList();
    return new JdbcIntelligence(
        rows, llm, routing, Integer.parseInt(s.get("LLM_DAILY_REQUEST_LIMIT", "50")), transactions);
  }

  @Bean
  DocumentResumeParser parser() {
    return new DocumentResumeParser();
  }

  @Bean
  Ports.ResumeRenderer renderer(DocumentResumeParser parser) {
    return new DeterministicResumeRenderer(parser);
  }

  @Bean
  Ports.ObjectStorage storage(PlatformSettings s) {
    return s.test()
        ? new LocalObjectStorage(Path.of(s.get("LOCAL_STORAGE_PATH", ".local/storage")))
        : new SupabaseObjectStorage(
            s.required("SUPABASE_URL"),
            s.required("STORAGE_BUCKET"),
            s.required("SUPABASE_SERVICE_ROLE_KEY"));
  }

  @Bean
  MailTransport mailTransport() {
    return new MailTransport();
  }

  @Bean
  GmailMailboxProvider gmail(MailTransport transport, JsonMapper json) {
    return new GmailMailboxProvider(transport, json);
  }

  @Bean
  OutlookMailboxProvider outlook(MailTransport transport) {
    return new OutlookMailboxProvider(transport);
  }

  @Bean
  TestMailboxProvider testMailbox() {
    return new TestMailboxProvider();
  }

  @Bean
  Ranking ranking(PlatformSettings s, Clock clock) {
    String[] raw = s.get("RANKING_WEIGHTS", "0.40,0.20,0.15,0.10,0.10,0.05").split(",");
    if (raw.length != 6) throw new IllegalArgumentException("RANKING_WEIGHTS requires six values");
    double[] w = Arrays.stream(raw).mapToDouble(Double::parseDouble).toArray();
    return new Ranking(new Ranking.Weights(w[0], w[1], w[2], w[3], w[4], w[5]), clock);
  }

  @Bean
  ProfileService profileService(Ports.Profiles p, Ports.Audit a) {
    return new ProfileService(p, a);
  }

  @Bean
  JobService jobService(
      Ports.Jobs j,
      Ports.Profiles p,
      Ports.Contacts c,
      Ports.Outbox o,
      Ports.Audit a,
      Ranking r,
      Clock clock) {
    return new JobService(j, p, c, o, a, r, clock);
  }

  @Bean
  ApplicationService applicationService(
      Ports.Applications a, JobService j, Ports.Audit audit, Clock clock) {
    return new ApplicationService(a, j, audit, clock);
  }

  @Bean
  ResumeService resumeService(
      Ports.Resumes r,
      Ports.Profiles p,
      Ports.ObjectStorage s,
      DocumentResumeParser parser,
      Ports.Outbox o,
      Ports.Audit a,
      JobService j,
      Clock clock) {
    return new ResumeService(r, p, s, parser, o, a, j, clock);
  }

  @Bean
  ApprovalService approvalService(
      Ports.Messages m,
      Ports.Resumes r,
      Ports.Contacts c,
      Ports.Outbox o,
      Ports.Audit a,
      Clock clock) {
    return new ApprovalService(m, r, c, o, a, clock);
  }

  @Bean
  GenerationService generationService(
      Ports.Profiles p,
      Ports.Resumes r,
      Ports.Messages m,
      Ports.Contacts c,
      Ports.Intelligence ai,
      Ports.ResumeRenderer renderer,
      Ports.ObjectStorage storage,
      Ports.Outbox o,
      Ports.Audit a,
      JobService j,
      Clock clock) {
    return new GenerationService(p, r, m, c, ai, renderer, storage, o, a, j, clock);
  }

  @Bean
  CommunicationService communicationService(
      Ports.Messages m,
      Ports.Contacts c,
      Ports.Resumes r,
      Ports.Applications applications,
      Ports.Audit a,
      Clock clock) {
    return new CommunicationService(m, c, r, applications, a, clock);
  }

  @Bean
  DiscoveryService discoveryService(
      List<Ports.JobSourceConnector> c,
      Ports.Connectors configs,
      Ports.Outbox o,
      JobService j,
      Clock clock) {
    return new DiscoveryService(c, configs, o, j, clock);
  }

  @Bean
  CaptureService captureService(PageExtraction e, JobService j) {
    return new CaptureService(e, j);
  }

  @Bean
  RecruiterService recruiterService(
      Ports.Contacts c,
      Ports.RecruiterResearch research,
      Ports.Outbox o,
      Ports.Audit a,
      JobService j,
      Clock clock) {
    return new RecruiterService(c, research, o, a, j, clock);
  }
}
