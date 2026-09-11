package io.myjobai.persistence;

import static io.myjobai.persistence.JsonRows.timestamp;

import io.myjobai.application.Ports;
import io.myjobai.domain.*;
import java.util.*;

public final class JdbcJobs implements Ports.Jobs {
  private final JsonRows rows;

  public JdbcJobs(JsonRows rows) {
    this.rows = rows;
  }

  public Opportunity.Job canonicalize(Opportunity.Job job, Opportunity.Source source) {
    String fingerprint = Normalization.fingerprint(job);
    rows.jdbc.queryForList(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", job.userId() + ":" + fingerprint);
    String external =
        source.externalId().isBlank() ? Normalization.hash(source.url()) : source.externalId();
    var existing =
        rows.one(
            "SELECT j.data FROM app.jobs j JOIN app.job_sources s ON s.job_id=j.id AND s.user_id=j.user_id WHERE s.user_id=? AND s.connector=? AND s.external_id=?",
            Opportunity.Job.class,
            job.userId(),
            source.connector(),
            external);
    if (existing.isEmpty())
      existing =
          rows.one(
              "SELECT data FROM app.jobs WHERE user_id=? AND (canonical_url=? OR (fingerprint=? AND posted_at BETWEEN ?::timestamptz-interval '45 days' AND ?::timestamptz+interval '45 days')) ORDER BY posted_at DESC LIMIT 1",
              Opportunity.Job.class,
              job.userId(),
              job.canonicalUrl(),
              fingerprint,
              timestamp(job.postedAt()),
              timestamp(job.postedAt()));
    Opportunity.Job canonical =
        existing.orElseGet(
            () ->
                rows.jdbc.queryForObject(
                    "INSERT INTO app.jobs(id,user_id,title,company,location,canonical_url,fingerprint,kind,posted_at,remote,salary_min,salary_max,currency,experience_years,data) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(user_id,canonical_url) DO UPDATE SET canonical_url=excluded.canonical_url RETURNING data",
                    (r, n) -> rows.read(r.getString(1), Opportunity.Job.class),
                    job.id(),
                    job.userId(),
                    job.title(),
                    job.company(),
                    job.location(),
                    job.canonicalUrl(),
                    fingerprint,
                    job.kind().name(),
                    timestamp(job.postedAt()),
                    job.remote(),
                    job.salaryMin(),
                    job.salaryMax(),
                    job.currency(),
                    job.experienceYears(),
                    rows.write(job)));
    var savedSource =
        new Opportunity.Source(
            source.id(),
            job.userId(),
            canonical.id(),
            source.connector(),
            external,
            source.url(),
            source.contentHash(),
            source.kind(),
            source.observedAt());
    rows.jdbc.update(
        "INSERT INTO app.job_sources(id,user_id,job_id,connector,external_id,url,content_hash,data) VALUES (?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(user_id,connector,external_id) DO UPDATE SET content_hash=excluded.content_hash,data=jsonb_set(excluded.data,'{id}',to_jsonb(app.job_sources.id::text))",
        savedSource.id(),
        savedSource.userId(),
        savedSource.jobId(),
        savedSource.connector(),
        external,
        savedSource.url(),
        savedSource.contentHash(),
        rows.write(savedSource));
    for (String skill : canonical.skills())
      rows.jdbc.update(
          "INSERT INTO app.job_requirements(user_id,job_id,skill) VALUES (?,?,?) ON CONFLICT DO NOTHING",
          canonical.userId(),
          canonical.id(),
          skill);
    return canonical;
  }

  public Optional<Opportunity.Job> find(UUID user, UUID id) {
    return rows.one(
        "SELECT data FROM app.jobs WHERE user_id=? AND id=?", Opportunity.Job.class, user, id);
  }

  public Opportunity.Page<Opportunity.Job> search(UUID user, Opportunity.Search q) {
    StringBuilder where = new StringBuilder(" WHERE j.user_id=?");
    List<Object> args = new ArrayList<>();
    args.add(user);
    if (q.role() != null && !q.role().isBlank()) {
      where.append(
          " AND to_tsvector('english',j.title || ' ' || j.company || ' ' || COALESCE(j.data->>'description','')) @@ plainto_tsquery('english',?)");
      args.add(q.role());
    }
    like(where, args, "j.company", q.company());
    like(where, args, "j.location", q.location());
    if (q.salaryMin() != null) {
      where.append(" AND j.salary_max>=?");
      args.add(q.salaryMin());
    }
    if (q.experienceMax() != null) {
      where.append(" AND j.experience_years<=?");
      args.add(q.experienceMax());
    }
    if (q.remote() != null) {
      where.append(" AND j.remote=?");
      args.add(q.remote());
    }
    if (q.since() != null) {
      where.append(" AND j.posted_at>=?");
      args.add(timestamp(q.since()));
    }
    if (q.source() != null && !q.source().isBlank()) {
      where.append(
          " AND EXISTS(SELECT 1 FROM app.job_sources s WHERE s.job_id=j.id AND s.user_id=j.user_id AND s.connector=?)");
      args.add(q.source());
    }
    if (q.referral() != null) {
      where
          .append(q.referral() ? " AND " : " AND NOT ")
          .append(
              "EXISTS(SELECT 1 FROM app.job_sources s WHERE s.job_id=j.id AND s.user_id=j.user_id AND s.data->>'kind'='REFERRAL_POST')");
    }
    if (q.hasRecruiter() != null) {
      where
          .append(q.hasRecruiter() ? " AND " : " AND NOT ")
          .append(
              "EXISTS(SELECT 1 FROM app.job_recruiters r JOIN app.contact_points c ON c.recruiter_id=r.recruiter_id AND c.user_id=r.user_id WHERE r.job_id=j.id AND r.user_id=j.user_id AND c.type='EMAIL' AND c.verification_status IN ('PUBLIC','PROVIDER_VERIFIED','USER_VERIFIED'))");
    }
    if (q.minScore() != null) {
      where.append(" AND m.score>=?");
      args.add(q.minScore());
    }
    String from =
        " FROM app.jobs j LEFT JOIN app.job_matches m ON m.job_id=j.id AND m.user_id=j.user_id";
    Long total =
        rows.jdbc.queryForObject("SELECT count(*)" + from + where, Long.class, args.toArray());
    var paged = new ArrayList<>(args);
    paged.add(q.size());
    paged.add(q.page() * q.size());
    return new Opportunity.Page<>(
        rows.list(
            "SELECT j.data"
                + from
                + where
                + " ORDER BY COALESCE(m.score,0) DESC,j.posted_at DESC,j.id LIMIT ? OFFSET ?",
            Opportunity.Job.class,
            paged.toArray()),
        q.page(),
        q.size(),
        total == null ? 0 : total);
  }

  private static void like(StringBuilder sql, List<Object> args, String column, String value) {
    if (value != null && !value.isBlank()) {
      sql.append(" AND ").append(column).append(" ILIKE ?");
      args.add("%" + value.replace("%", "\\%").replace("_", "\\_") + "%");
    }
  }

  public List<Opportunity.Source> sources(UUID user, UUID job) {
    return rows.list(
        "SELECT data FROM app.job_sources WHERE user_id=? AND job_id=? ORDER BY id",
        Opportunity.Source.class,
        user,
        job);
  }

  public void saveMatch(Opportunity.Match match) {
    rows.jdbc.update(
        "INSERT INTO app.job_matches(user_id,job_id,score,eligible,data,calculated_at) VALUES (?,?,?,?,?::jsonb,?) ON CONFLICT(job_id,user_id) DO UPDATE SET score=excluded.score,eligible=excluded.eligible,data=excluded.data,calculated_at=excluded.calculated_at",
        match.userId(),
        match.jobId(),
        match.score(),
        match.eligible(),
        rows.write(match),
        timestamp(match.calculatedAt()));
  }

  public Optional<Opportunity.Match> match(UUID user, UUID job) {
    return rows.one(
        "SELECT data FROM app.job_matches WHERE user_id=? AND job_id=?",
        Opportunity.Match.class,
        user,
        job);
  }
}
