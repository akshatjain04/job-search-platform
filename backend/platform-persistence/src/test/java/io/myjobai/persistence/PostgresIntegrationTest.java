package io.myjobai.persistence;

import static org.assertj.core.api.Assertions.*;

import io.myjobai.application.*;
import io.myjobai.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class PostgresIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("myjobai_test");

  static JsonRows rows;
  static TransactionTemplate tx;
  static JdbcOutbox outbox;
  static JdbcJobs jobs;
  UUID user;
  Instant now = Instant.parse("2026-09-11T00:00:00Z");

  @BeforeAll
  static void migrateEmptyDatabase() {
    var source = new PGSimpleDataSource();
    source.setURL(POSTGRES.getJdbcUrl());
    source.setUser(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    Flyway.configure()
        .dataSource(source)
        .schemas("app")
        .defaultSchema("app")
        .locations("classpath:db/migration")
        .load()
        .migrate();
    Flyway.configure()
        .dataSource(source)
        .schemas("app")
        .defaultSchema("app")
        .locations("classpath:db/migration")
        .load()
        .validate();
    rows = new JsonRows(new JdbcTemplate(source), JsonMapper.builder().findAndAddModules().build());
    tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    outbox = new JdbcOutbox(rows);
    jobs = new JdbcJobs(rows);
  }

  @BeforeEach
  void createUser() {
    user = UUID.randomUUID();
    rows.jdbc.update("INSERT INTO app.users(id,email) VALUES (?,?)", user, "test@example.com");
  }

  Opportunity.Job job(String url) {
    return new Opportunity.Job(
        UUID.randomUUID(),
        user,
        "Java Engineer",
        "Acme",
        "Remote",
        "Build Java services",
        Opportunity.Kind.JOB_POSTING,
        true,
        "FULL_TIME",
        null,
        null,
        "",
        2,
        List.of("Java"),
        now,
        url);
  }

  Opportunity.Job saveJob(Opportunity.Job job, String connector) {
    return tx.execute(
        s ->
            jobs.canonicalize(
                job,
                new Opportunity.Source(
                    UUID.randomUUID(),
                    user,
                    job.id(),
                    connector,
                    job.id().toString(),
                    job.canonicalUrl(),
                    Normalization.hash(job.description()),
                    job.kind(),
                    now)));
  }

  @Test
  void allCoreTablesAndIndexesAreMigrated() {
    var names =
        rows.jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='app'",
            String.class);
    assertThat(names)
        .contains(
            "users",
            "candidate_profiles",
            "experience_facts",
            "resumes",
            "resume_versions",
            "jobs",
            "job_sources",
            "job_requirements",
            "job_matches",
            "recruiters",
            "contact_points",
            "job_recruiters",
            "applications",
            "application_events",
            "outreach_messages",
            "outreach_versions",
            "approvals",
            "connector_configs",
            "connector_runs",
            "audit_events",
            "outbox_events");
    assertThat(
            rows.jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname='app'", String.class))
        .contains("outbox_claim", "jobs_fts", "job_matches_rank", "approvals_current");
  }

  @Test
  void billedUsageSurvivesDownstreamRollbackAndFailuresCountTowardBudget() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    Ports.LlmProvider provider =
        new Ports.LlmProvider() {
          public String key() {
            return "fake";
          }

          public Ports.Completion generate(Ports.LlmRequest request) {
            calls.incrementAndGet();
            if (request.task().equals("failure"))
              throw new IntegrationException("PROVIDER_FAILURE", "Safe error", false, false);
            return new Ports.Completion(
                Map.of("ok", true), new Ports.Usage("fake", "model", 10, 5, 20, 0.01));
          }
        };
    var service = new JdbcIntelligence(rows, provider, "fake-model", 2, tx.getTransactionManager());
    var request = new Ports.LlmRequest("success", Ports.Tier.CHEAP, "trusted", Map.of(), Map.of());
    assertThatThrownBy(
            () ->
                tx.execute(
                    s -> {
                      service.generate(user, request);
                      throw new IllegalStateException("renderer failed");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.generate(user, request).output()).containsEntry("ok", true);
    assertThat(calls.get()).isEqualTo(1);
    var failure = new Ports.LlmRequest("failure", Ports.Tier.CHEAP, "trusted", Map.of(), Map.of());
    assertThatThrownBy(() -> service.generate(user, failure))
        .isInstanceOf(IntegrationException.class);
    assertThat(
            rows.jdbc.queryForObject(
                "SELECT count(*) FROM app.ai_usage WHERE user_id=? AND NOT cached",
                Long.class,
                user))
        .isEqualTo(2);
    assertThatThrownBy(() -> service.generate(user, failure)).hasMessageContaining("budget");
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void dedupPreservesEverySourceAndDoesNotLeakUsers() {
    var first = saveJob(job("https://first.example/jobs/1"), "greenhouse");
    var second = saveJob(job("https://second.example/jobs/2"), "lever");
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(jobs.sources(user, first.id())).hasSize(2);
    assertThat(jobs.find(UUID.randomUUID(), first.id())).isEmpty();
    assertThat(jobs.search(user, Opportunity.Search.first()).total()).isEqualTo(1);
  }

  @Test
  void aggregateAndOutboxRollBackTogether() {
    UUID jobId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                tx.execute(
                    s -> {
                      rows.jdbc.update(
                          "INSERT INTO app.candidate_profiles(user_id,data) VALUES (?,'{}')", user);
                      outbox.enqueue(
                          user, "ROLLBACK_TEST", Map.of("jobId", jobId.toString()), "same", now);
                      throw new IllegalStateException("rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            rows.jdbc.queryForObject(
                "SELECT count(*) FROM app.candidate_profiles WHERE user_id=?", Long.class, user))
        .isZero();
    assertThat(outbox.list(user)).isEmpty();
  }

  @Test
  void idempotencyReturnsSameJobAndRejectsDifferentPayload() {
    var a = outbox.enqueue(user, "TEST", Map.of("x", "1"), "key", now);
    var b = outbox.enqueue(user, "TEST", Map.of("x", "1"), "key", now);
    assertThat(a.id()).isEqualTo(b.id());
    assertThatThrownBy(() -> outbox.enqueue(user, "TEST", Map.of("x", "2"), "key", now))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void concurrentWorkersClaimEachJobOnce() throws Exception {
    String type = "PARALLEL_" + user;
    for (int i = 0; i < 40; i++)
      outbox.enqueue(user, type, Map.of("n", Integer.toString(i)), Integer.toString(i), now);
    try (var executor = Executors.newFixedThreadPool(4)) {
      var futures = new ArrayList<Future<Set<UUID>>>();
      for (int i = 0; i < 4; i++)
        futures.add(
            executor.submit(
                () -> {
                  var claimed = new HashSet<UUID>();
                  while (true) {
                    var next =
                        outbox.claim(
                            Set.of(type), UUID.randomUUID().toString(), now, Duration.ofMinutes(5));
                    if (next.isEmpty()) break;
                    assertThat(claimed.add(next.get().id())).isTrue();
                    outbox.complete(next.get(), now);
                  }
                  return claimed;
                }));
      var all = new HashSet<UUID>();
      int total = 0;
      for (var future : futures) {
        var ids = future.get(30, TimeUnit.SECONDS);
        total += ids.size();
        all.addAll(ids);
      }
      assertThat(total).isEqualTo(40);
      assertThat(all).hasSize(40);
    }
  }

  @Test
  void retryAndLeaseRecoveryAreFencedAgainstOldWorkers() {
    String type = "LEASE_" + user;
    outbox.enqueue(user, type, Map.of(), "key", now);
    var old = outbox.claim(Set.of(type), "old", now, Duration.ofMinutes(1)).orElseThrow();
    var replacement =
        outbox
            .claim(Set.of(type), "replacement", now.plusSeconds(61), Duration.ofMinutes(1))
            .orElseThrow();
    outbox.complete(old, now.plusSeconds(62));
    assertThat(outbox.find(user, old.id()).orElseThrow().status())
        .isEqualTo(AsyncJob.Status.PROCESSING);
    outbox.fail(replacement, "temporary", true, now.plusSeconds(62));
    assertThat(outbox.claim(Set.of(type), "too-early", now.plusSeconds(63), Duration.ofMinutes(1)))
        .isEmpty();
    var retried =
        outbox
            .claim(Set.of(type), "retry", now.plusSeconds(80), Duration.ofMinutes(1))
            .orElseThrow();
    outbox.fail(retried, "permanent", false, now.plusSeconds(80));
    assertThat(outbox.find(user, old.id()).orElseThrow().status())
        .isEqualTo(AsyncJob.Status.FAILED);
  }

  @Test
  void compositeForeignKeysRejectCrossUserAttachments() {
    var ownedJob = saveJob(job("https://first.example/job"), "manual");
    UUID stranger = UUID.randomUUID();
    rows.jdbc.update(
        "INSERT INTO app.users(id,email) VALUES (?,?)", stranger, "stranger@example.com");
    assertThatThrownBy(
            () ->
                rows.jdbc.update(
                    "INSERT INTO app.applications(id,user_id,job_id,state,created_at,updated_at) VALUES (?,?,?,'SAVED',now(),now())",
                    UUID.randomUUID(),
                    stranger,
                    ownedJob.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void applicationEventsCannotBeOverwritten() {
    var ownedJob = saveJob(job("https://first.example/job"), "manual");
    var repo = new JdbcApplications(rows);
    var app = tx.execute(s -> repo.create(user, ownedJob.id(), now));
    tx.executeWithoutResult(
        s -> repo.transition(app, ApplicationLifecycle.State.SAVED, "Saved", now.plusSeconds(1)));
    assertThat(repo.events(user, app.id())).hasSize(2);
    assertThatThrownBy(
            () ->
                rows.jdbc.update(
                    "UPDATE app.application_events SET note='tampered' WHERE application_id=?",
                    app.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(repo.events(UUID.randomUUID(), app.id())).isEmpty();
  }
}
