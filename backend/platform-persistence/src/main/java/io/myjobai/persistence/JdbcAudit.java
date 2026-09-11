package io.myjobai.persistence;

import io.myjobai.application.Ports;
import java.util.*;

public final class JdbcAudit implements Ports.Audit {
  private final JsonRows rows;

  public JdbcAudit(JsonRows rows) {
    this.rows = rows;
  }

  public void record(
      UUID user, String action, String resourceType, UUID resource, Map<String, String> metadata) {
    rows.jdbc.update(
        "INSERT INTO app.audit_events(id,user_id,action,resource_type,resource_id,metadata) VALUES (?,?,?,?,?,?::jsonb)",
        UUID.randomUUID(),
        user,
        action,
        resourceType,
        resource,
        rows.write(metadata));
  }
}
