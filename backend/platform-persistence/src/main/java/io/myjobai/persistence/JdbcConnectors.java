package io.myjobai.persistence;

import static io.myjobai.persistence.JsonRows.*;

import io.myjobai.application.Ports;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;

public final class JdbcConnectors implements Ports.Connectors {
  private final JsonRows rows;
  private final RowMapper<Ports.ConnectorConfig> mapper =
      (r, n) ->
          new Ports.ConnectorConfig(
              uuid(r, "id"),
              uuid(r, "user_id"),
              r.getString("connector"),
              r.getString("board"),
              r.getString("role"),
              r.getInt("interval_minutes"),
              r.getBoolean("enabled"),
              instant(r, "next_run_at"));

  public JdbcConnectors(JsonRows rows) {
    this.rows = rows;
  }

  public List<Ports.ConnectorConfig> list(UUID user) {
    return rows.jdbc.query(
        "SELECT * FROM app.connector_configs WHERE user_id=? ORDER BY connector,board",
        mapper,
        user);
  }

  public void save(Ports.ConnectorConfig c) {
    rows.jdbc.update(
        "INSERT INTO app.connector_configs(id,user_id,connector,board,role,interval_minutes,enabled,next_run_at) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(user_id,connector,board) DO UPDATE SET role=excluded.role,interval_minutes=excluded.interval_minutes,enabled=excluded.enabled",
        c.id(),
        c.userId(),
        c.connector(),
        c.board(),
        c.role(),
        c.intervalMinutes(),
        c.enabled(),
        timestamp(c.nextRunAt()));
  }

  public List<Ports.ConnectorConfig> due(Instant now) {
    return rows.jdbc.query(
        "SELECT * FROM app.connector_configs WHERE enabled AND next_run_at<=? ORDER BY next_run_at LIMIT 20",
        mapper,
        timestamp(now));
  }

  public void schedule(UUID id, Instant next) {
    rows.jdbc.update(
        "UPDATE app.connector_configs SET next_run_at=? WHERE id=?", timestamp(next), id);
  }

  public void run(
      UUID config, UUID user, String status, int discovered, String error, Instant now) {
    rows.jdbc.update(
        "INSERT INTO app.connector_runs(id,user_id,config_id,status,discovered,error,at) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        user,
        config,
        status,
        discovered,
        error,
        timestamp(now));
  }
}
