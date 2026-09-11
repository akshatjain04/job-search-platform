package io.myjobai.persistence;

import static io.myjobai.persistence.JsonRows.*;

import io.myjobai.application.Ports;
import io.myjobai.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;

public final class JdbcMessages implements Ports.Messages {
  private final JsonRows rows;
  private final RowMapper<Outreach.Message> mapper =
      (r, n) ->
          new Outreach.Message(
              uuid(r, "id"),
              uuid(r, "user_id"),
              uuid(r, "job_id"),
              Outreach.Channel.valueOf(r.getString("channel")),
              Outreach.State.valueOf(r.getString("state")),
              uuid(r, "current_version_id"),
              instant(r, "updated_at"));

  public JdbcMessages(JsonRows rows) {
    this.rows = rows;
  }

  public void create(Outreach.Message message, Outreach.Version version) {
    rows.jdbc.update(
        "INSERT INTO app.outreach_messages(id,user_id,job_id,channel,state,current_version_id,updated_at) VALUES (?,?,?,?,?,?,?)",
        message.id(),
        message.userId(),
        message.jobId(),
        message.channel().name(),
        message.state().name(),
        version.id(),
        timestamp(message.updatedAt()));
    insertVersion(version);
  }

  private void insertVersion(Outreach.Version version) {
    rows.jdbc.update(
        "INSERT INTO app.outreach_versions(id,user_id,message_id,recipient_id,resume_version_id,data) VALUES (?,?,?,?,?,?::jsonb)",
        version.id(),
        version.userId(),
        version.messageId(),
        version.recipientId(),
        version.resumeVersionId(),
        rows.write(version));
  }

  public Optional<Outreach.Message> find(UUID user, UUID id, boolean lock) {
    return rows
        .jdbc
        .query(
            "SELECT * FROM app.outreach_messages WHERE user_id=? AND id=?"
                + (lock ? " FOR UPDATE" : ""),
            mapper,
            user,
            id)
        .stream()
        .findFirst();
  }

  public Optional<Outreach.Version> version(UUID user, UUID id) {
    return rows.one(
        "SELECT data FROM app.outreach_versions WHERE user_id=? AND id=?",
        Outreach.Version.class,
        user,
        id);
  }

  public List<Outreach.Message> list(UUID user) {
    return rows.jdbc.query(
        "SELECT * FROM app.outreach_messages WHERE user_id=? ORDER BY updated_at DESC",
        mapper,
        user);
  }

  public List<Outreach.Version> versions(UUID user, UUID message) {
    return rows.list(
        "SELECT data FROM app.outreach_versions WHERE user_id=? AND message_id=? ORDER BY created_at DESC",
        Outreach.Version.class,
        user,
        message);
  }

  public void revise(Outreach.Message message, Outreach.Version version, Instant now) {
    insertVersion(version);
    invalidate(message.userId(), message.id(), now);
    rows.jdbc.update(
        "UPDATE app.outreach_messages SET current_version_id=?,state='DRAFT',updated_at=? WHERE user_id=? AND id=?",
        version.id(),
        timestamp(now),
        message.userId(),
        message.id());
  }

  public void state(UUID user, UUID message, Outreach.State state, Instant now) {
    if (rows.jdbc.update(
            "UPDATE app.outreach_messages SET state=?,updated_at=? WHERE user_id=? AND id=?",
            state.name(),
            timestamp(now),
            user,
            message)
        != 1) throw DomainException.missing();
  }

  public void approve(Outreach.Approval a) {
    rows.jdbc.update(
        "INSERT INTO app.approvals(id,user_id,message_id,email_version_id,resume_version_id,recipient_id,fingerprint,approved_at) VALUES (?,?,?,?,?,?,?,?)",
        a.id(),
        a.userId(),
        a.messageId(),
        a.emailVersionId(),
        a.resumeVersionId(),
        a.recipientId(),
        a.fingerprint(),
        timestamp(a.approvedAt()));
  }

  public Optional<Outreach.Approval> approval(UUID user, UUID id) {
    return rows
        .jdbc
        .query(
            "SELECT * FROM app.approvals WHERE user_id=? AND id=?",
            (r, n) ->
                new Outreach.Approval(
                    uuid(r, "id"),
                    uuid(r, "user_id"),
                    uuid(r, "message_id"),
                    uuid(r, "email_version_id"),
                    uuid(r, "resume_version_id"),
                    uuid(r, "recipient_id"),
                    r.getString("fingerprint"),
                    instant(r, "approved_at"),
                    instant(r, "invalidated_at")),
            user,
            id)
        .stream()
        .findFirst();
  }

  public Optional<Outreach.Approval> currentApproval(UUID user, UUID message) {
    return rows
        .jdbc
        .query(
            "SELECT id FROM app.approvals WHERE user_id=? AND message_id=? AND invalidated_at IS NULL",
            (r, n) -> uuid(r, "id"),
            user,
            message)
        .stream()
        .findFirst()
        .flatMap(id -> approval(user, id));
  }

  public void invalidate(UUID user, UUID message, Instant now) {
    rows.jdbc.update(
        "UPDATE app.approvals SET invalidated_at=? WHERE user_id=? AND message_id=? AND invalidated_at IS NULL",
        timestamp(now),
        user,
        message);
  }
}
