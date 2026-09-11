package io.myjobai.persistence;

import static io.myjobai.persistence.JsonRows.*;

import io.myjobai.application.Ports;
import io.myjobai.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.type.TypeReference;

public final class JdbcOutbox implements Ports.Outbox {
  private final JsonRows rows;
  private final RowMapper<AsyncJob> mapper;

  public JdbcOutbox(JsonRows rows) {
    this.rows = rows;
    mapper =
        (r, n) ->
            new AsyncJob(
                uuid(r, "id"),
                uuid(r, "user_id"),
                r.getString("event_type"),
                rows.json.readValue(
                    r.getString("payload"), new TypeReference<Map<String, String>>() {}),
                AsyncJob.Status.valueOf(r.getString("status")),
                r.getInt("attempt_count"),
                r.getInt("max_attempts"),
                instant(r, "available_at"),
                instant(r, "locked_at"),
                r.getString("locked_by"),
                r.getString("last_error"),
                instant(r, "created_at"),
                instant(r, "completed_at"),
                r.getString("idempotency_key"));
  }

  public AsyncJob enqueue(
      UUID user, String type, Map<String, String> payload, String key, Instant now) {
    UUID id = UUID.randomUUID();
    rows.jdbc.update(
        "INSERT INTO app.outbox_events(id,user_id,event_type,payload,available_at,created_at,updated_at,idempotency_key) VALUES (?,?,?,?::jsonb,?,?,?,?) ON CONFLICT(user_id,event_type,idempotency_key) DO NOTHING",
        id,
        user,
        type,
        rows.write(payload),
        timestamp(now),
        timestamp(now),
        timestamp(now),
        key);
    var job =
        rows.jdbc.queryForObject(
            "SELECT * FROM app.outbox_events WHERE user_id=? AND event_type=? AND idempotency_key=?",
            mapper,
            user,
            type,
            key);
    if (job == null || !job.payload().equals(payload))
      throw DomainException.conflict("Idempotency key was already used with different input");
    return job;
  }

  public Optional<AsyncJob> claim(
      Set<String> types, String claimToken, Instant now, Duration lease) {
    if (types.isEmpty()) return Optional.empty();
    rows.jdbc.update(
        "UPDATE app.outbox_events SET status='FAILED',last_error='Worker lease expired after final attempt',updated_at=? WHERE status='PROCESSING' AND locked_at<? AND attempt_count>=max_attempts",
        timestamp(now),
        timestamp(now.minus(lease)));
    String placeholders = String.join(",", Collections.nCopies(types.size(), "?"));
    String sql =
        "WITH next AS (SELECT id FROM app.outbox_events WHERE event_type IN ("
            + placeholders
            + ") AND attempt_count<max_attempts AND ((status='PENDING' AND available_at<=?) OR (status='PROCESSING' AND locked_at<?)) ORDER BY available_at,created_at FOR UPDATE SKIP LOCKED LIMIT 1) UPDATE app.outbox_events e SET status='PROCESSING',attempt_count=e.attempt_count+1,locked_at=?,locked_by=?,updated_at=? FROM next WHERE e.id=next.id RETURNING e.*";
    var args = new ArrayList<Object>(types);
    args.add(timestamp(now));
    args.add(timestamp(now.minus(lease)));
    args.add(timestamp(now));
    args.add(claimToken);
    args.add(timestamp(now));
    return rows.jdbc.query(sql, mapper, args.toArray()).stream().findFirst();
  }

  public void complete(AsyncJob job, Instant now) {
    rows.jdbc.update(
        "UPDATE app.outbox_events SET status='COMPLETED',completed_at=?,updated_at=?,locked_at=NULL,locked_by=NULL WHERE id=? AND status='PROCESSING' AND locked_by=?",
        timestamp(now),
        timestamp(now),
        job.id(),
        job.lockedBy());
  }

  public void fail(AsyncJob job, String error, boolean retryable, Instant now) {
    boolean retry = retryable && job.attemptCount() < job.maxAttempts();
    rows.jdbc.update(
        "UPDATE app.outbox_events SET status=?,last_error=?,available_at=?,updated_at=?,locked_at=NULL,locked_by=NULL WHERE id=? AND status='PROCESSING' AND locked_by=?",
        retry ? "PENDING" : "FAILED",
        Checks.optional(error, 500),
        timestamp(now.plus(AsyncJob.retryDelay(job.attemptCount()))),
        timestamp(now),
        job.id(),
        job.lockedBy());
  }

  public List<AsyncJob> list(UUID user) {
    return rows.jdbc.query(
        "SELECT * FROM app.outbox_events WHERE user_id=? ORDER BY created_at DESC LIMIT 100",
        mapper,
        user);
  }

  public Optional<AsyncJob> find(UUID user, UUID id) {
    return rows
        .jdbc
        .query("SELECT * FROM app.outbox_events WHERE user_id=? AND id=?", mapper, user, id)
        .stream()
        .findFirst();
  }

  public void retry(UUID user, UUID id, Instant now) {
    if (rows.jdbc.update(
            "UPDATE app.outbox_events SET status='PENDING',attempt_count=0,available_at=?,updated_at=?,last_error=NULL WHERE user_id=? AND id=? AND status='FAILED' AND event_type<>'SEND_EMAIL'",
            timestamp(now),
            timestamp(now),
            user,
            id)
        != 1)
      throw DomainException.conflict(
          "Only failed non-email tasks can be retried; email requires review and a new approval");
  }
}
