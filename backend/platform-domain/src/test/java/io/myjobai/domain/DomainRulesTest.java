package io.myjobai.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainRulesTest {
  private final UUID user = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-09-11T00:00:00Z");

  private Opportunity.Job job(String url, Opportunity.Kind kind, Instant posted) {
    return new Opportunity.Job(
        UUID.randomUUID(),
        user,
        "Java Engineer",
        "Acme",
        "Remote",
        "Build Java services",
        kind,
        true,
        "FULL_TIME",
        100,
        200,
        "USD",
        2,
        List.of("Java"),
        posted,
        url);
  }

  private Candidate.Profile profile() {
    return new Candidate.Profile(
        user,
        new Candidate.Identity("Alice", "alice@example.com", "", "", null),
        null,
        null,
        null,
        List.of("Java"),
        null,
        null,
        null);
  }

  private Candidate.Fact fact() {
    return new Candidate.Fact(
        UUID.randomUUID(),
        user,
        "Acme",
        "Engineer",
        "Built Java services",
        List.of("Java"),
        null,
        null,
        null,
        "User reviewed resume page 1",
        true);
  }

  @Test
  void canonicalizationKeepsJobIdentityButDropsTracking() {
    assertThat(Normalization.url("https://EXAMPLE.com/jobs/123/?utm_source=x&job=123"))
        .isEqualTo("https://example.com/jobs/123?job=123");
    assertThat(
            Normalization.sameJob(
                job("https://a.example/jobs/1", Opportunity.Kind.JOB_POSTING, now),
                job("https://b.example/2", Opportunity.Kind.JOB_POSTING, now)))
        .isTrue();
    assertThat(
            Normalization.sameJob(
                job("https://a.example/1", Opportunity.Kind.JOB_POSTING, now),
                job(
                    "https://b.example/2",
                    Opportunity.Kind.JOB_POSTING,
                    now.minus(Duration.ofDays(60)))))
        .isFalse();
  }

  @Test
  void freshReferralsOutrankListingsAndUnverifiedSkillsDoNotCount() {
    var ranking = new Ranking(Ranking.Weights.defaults(), Clock.fixed(now, ZoneOffset.UTC));
    var referral =
        ranking.match(
            profile(),
            List.of(fact()),
            job("https://example.com/1", Opportunity.Kind.REFERRAL_POST, now),
            true);
    var listing =
        ranking.match(
            profile(),
            List.of(fact()),
            job(
                "https://example.com/2",
                Opportunity.Kind.JOB_POSTING,
                now.minus(Duration.ofDays(20))),
            false);
    assertThat(referral.score()).isGreaterThan(listing.score());
    assertThat(
            ranking
                .match(
                    profile(),
                    List.of(),
                    job("https://example.com/1", Opportunity.Kind.JOB_POSTING, now),
                    false)
                .missingSkills())
        .containsExactly("Java");
  }

  @Test
  void rejectsUnprovenClaimsAndEmployers() {
    var fact = fact();
    var content =
        new Resume.Structured(
            profile().identity(),
            List.of(
                new Resume.Section(
                    "Acme — Engineer", List.of(new Resume.Bullet(fact.id(), fact.statement())))),
            List.of("Java"));
    assertThatCode(() -> Resume.validateGrounding(content, profile(), List.of(fact)))
        .doesNotThrowAnyException();
    var invented =
        new Resume.Structured(
            profile().identity(),
            List.of(
                new Resume.Section(
                    "Acme — Engineer",
                    List.of(new Resume.Bullet(fact.id(), "Increased revenue 900%")))),
            List.of("Java"));
    assertThatThrownBy(() -> Resume.validateGrounding(invented, profile(), List.of(fact)))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void scoreRequiresBothCompatibilityAndParsing() {
    var fact = fact();
    var content =
        new Resume.Structured(
            profile().identity(),
            List.of(
                new Resume.Section(
                    "Acme — Engineer", List.of(new Resume.Bullet(fact.id(), fact.statement())))),
            List.of("Java"));
    String text = "Alice alice@example.com Acme Engineer Built Java services Java";
    assertThat(
            Compatibility.evaluate(
                    content,
                    job("https://example.com/job", Opportunity.Kind.JOB_POSTING, now),
                    text,
                    text)
                .recommended())
        .isTrue();
    assertThat(
            Compatibility.evaluate(
                    content,
                    job("https://example.com/job", Opportunity.Kind.JOB_POSTING, now),
                    "Alice alice@example.com Built Java services Java",
                    text)
                .recommended())
        .isFalse();
    assertThat(
            Compatibility.evaluate(
                    content,
                    job("https://example.com/job", Opportunity.Kind.JOB_POSTING, now),
                    "",
                    text)
                .recommended())
        .isFalse();
  }

  @Test
  void lifecycleRejectsSkipsAndTerminalMutations() {
    assertThatCode(
            () ->
                ApplicationLifecycle.requireTransition(
                    ApplicationLifecycle.State.SAVED, ApplicationLifecycle.State.SHORTLISTED))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                ApplicationLifecycle.requireTransition(
                    ApplicationLifecycle.State.DISCOVERED, ApplicationLifecycle.State.OFFER))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                ApplicationLifecycle.requireTransition(
                    ApplicationLifecycle.State.REJECTED, ApplicationLifecycle.State.SAVED))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void inferredContactsAreNeverSendable() {
    var contact =
        new Contact(
            UUID.randomUUID(),
            user,
            UUID.randomUUID(),
            "Recruiter",
            "recruiter@example.com",
            Contact.Type.EMAIL,
            "https://example.com/team",
            "MANUAL",
            .5,
            Contact.Verification.INFERRED,
            now,
            "Inferred pattern");
    assertThat(contact.sendable()).isFalse();
    assertThatThrownBy(() -> Checks.publicUrl("http://127.0.0.1/private"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void retryIsExponentialAndBounded() {
    assertThat(AsyncJob.retryDelay(1)).isEqualTo(Duration.ofSeconds(5));
    assertThat(AsyncJob.retryDelay(3)).isEqualTo(Duration.ofSeconds(20));
    assertThat(AsyncJob.retryDelay(100)).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void approvalBindsEveryVersionAndRecipientField() {
    var contact =
        new Contact(
            UUID.randomUUID(),
            user,
            UUID.randomUUID(),
            "Recruiter",
            "recruiter@example.com",
            Contact.Type.EMAIL,
            "https://example.com/team",
            "PUBLIC_PAGE",
            1,
            Contact.Verification.PUBLIC,
            now,
            "Published mailto");
    UUID messageId = UUID.randomUUID(), resumeId = UUID.randomUUID();
    var version =
        new Outreach.Version(
            UUID.randomUUID(),
            user,
            messageId,
            contact.id(),
            contact.value(),
            "Application",
            "Hello",
            resumeId,
            "abc",
            now);
    var message =
        new Outreach.Message(
            messageId,
            user,
            UUID.randomUUID(),
            Outreach.Channel.EMAIL,
            Outreach.State.QUEUED,
            version.id(),
            now);
    var resume =
        new Resume.Version(
            resumeId,
            user,
            UUID.randomUUID(),
            message.jobId(),
            null,
            "pdf",
            "docx",
            "abc",
            "def",
            List.of(),
            now);
    var approval =
        new Outreach.Approval(
            UUID.randomUUID(),
            user,
            messageId,
            version.id(),
            resumeId,
            contact.id(),
            version.fingerprint(),
            now,
            null);
    assertThatCode(() -> approval.requireValid(message, version, contact, resume))
        .doesNotThrowAnyException();
    var changed =
        new Outreach.Version(
            version.id(),
            user,
            messageId,
            contact.id(),
            contact.value(),
            "Changed",
            "Hello",
            resumeId,
            "abc",
            now);
    assertThatThrownBy(() -> approval.requireValid(message, changed, contact, resume))
        .isInstanceOf(DomainException.class);
  }
}
