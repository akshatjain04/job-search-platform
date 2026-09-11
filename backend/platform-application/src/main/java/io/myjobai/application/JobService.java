package io.myjobai.application;

import io.myjobai.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

public class JobService {
  private final Ports.Jobs jobs;
  private final Ports.Profiles profiles;
  private final Ports.Contacts contacts;
  private final Ports.Outbox outbox;
  private final Ports.Audit audit;
  private final Ranking ranking;
  private final Clock clock;

  public JobService(
      Ports.Jobs jobs,
      Ports.Profiles profiles,
      Ports.Contacts contacts,
      Ports.Outbox outbox,
      Ports.Audit audit,
      Ranking ranking,
      Clock clock) {
    this.jobs = jobs;
    this.profiles = profiles;
    this.contacts = contacts;
    this.outbox = outbox;
    this.audit = audit;
    this.ranking = ranking;
    this.clock = clock;
  }

  public Opportunity.Job get(UUID user, UUID id) {
    return jobs.find(user, id).orElseThrow(DomainException::missing);
  }

  public Opportunity.Page<Opportunity.Job> search(UUID user, Opportunity.Search query) {
    return jobs.search(user, query);
  }

  public List<Opportunity.Source> sources(UUID user, UUID id) {
    get(user, id);
    return jobs.sources(user, id);
  }

  @Transactional
  public Opportunity.Job ingest(UUID user, Ports.DiscoveredJob data, String connector) {
    var job =
        new Opportunity.Job(
            UUID.randomUUID(),
            user,
            data.title(),
            data.company(),
            data.location(),
            data.description(),
            data.kind(),
            data.remote(),
            data.employmentType(),
            data.salaryMin(),
            data.salaryMax(),
            data.currency(),
            data.experienceYears(),
            data.skills(),
            data.postedAt() == null ? clock.instant() : data.postedAt(),
            Normalization.url(data.url()));
    var source =
        new Opportunity.Source(
            UUID.randomUUID(),
            user,
            job.id(),
            Checks.text(connector, "Source", 80),
            Checks.optional(data.externalId(), 300),
            job.canonicalUrl(),
            Normalization.hash(Normalization.text(job.description())),
            job.kind(),
            clock.instant());
    var canonical = jobs.canonicalize(job, source);
    outbox.enqueue(
        user,
        "MATCH_JOB",
        Map.of("jobId", canonical.id().toString()),
        canonical.id() + ":" + source.contentHash(),
        clock.instant());
    audit.record(user, "JOB_CAPTURED", "job", canonical.id(), Map.of("source", connector));
    return canonical;
  }

  @Transactional
  public Opportunity.Match analyze(UUID user, UUID id) {
    var job = get(user, id);
    var profile =
        profiles
            .find(user)
            .orElseThrow(
                () -> DomainException.invalid("Create your candidate profile before matching"));
    var strongest =
        jobs.sources(user, id).stream()
            .map(Opportunity.Source::kind)
            .max(Comparator.comparingInt(Enum::ordinal))
            .orElse(job.kind());
    var rankingInput =
        new Opportunity.Job(
            job.id(),
            job.userId(),
            job.title(),
            job.company(),
            job.location(),
            job.description(),
            strongest,
            job.remote(),
            job.employmentType(),
            job.salaryMin(),
            job.salaryMax(),
            job.currency(),
            job.experienceYears(),
            job.skills(),
            job.postedAt(),
            job.canonicalUrl());
    var match =
        ranking.match(
            profile,
            profiles.facts(user),
            rankingInput,
            contacts.forJob(user, id).stream().anyMatch(Contact::sendable));
    jobs.saveMatch(match);
    if (match.eligible() && match.score() >= profile.preferences().alertThreshold())
      audit.record(user, "MATCH_ALERT", "job", id, Map.of("score", Double.toString(match.score())));
    return match;
  }

  public Opportunity.Match match(UUID user, UUID id) {
    get(user, id);
    return jobs.match(user, id).orElseThrow(DomainException::missing);
  }
}
