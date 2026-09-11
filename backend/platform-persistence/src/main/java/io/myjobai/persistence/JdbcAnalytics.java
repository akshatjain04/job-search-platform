package io.myjobai.persistence;

import io.myjobai.application.Ports;
import java.util.*;

/** Read-only projections, with explicit owner predicates and no causal attribution claims. */
public final class JdbcAnalytics implements Ports.Analytics {
  private final JsonRows rows;

  public JdbcAnalytics(JsonRows rows) {
    this.rows = rows;
  }

  private Map<String, Long> counts(String sql, UUID user) {
    var result = new LinkedHashMap<String, Long>();
    rows.jdbc.query(
        sql,
        r -> {
          result.put(r.getString(1), r.getLong(2));
        },
        user);
    return result;
  }

  private List<Ports.Performance> performance(String sql, UUID user) {
    return rows.jdbc.query(
        sql,
        (r, n) ->
            new Ports.Performance(
                r.getString(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5)),
        user);
  }

  public Ports.AnalyticsSummary summary(UUID user) {
    var lifecycle =
        counts(
            "SELECT to_state,count(DISTINCT application_id) FROM app.application_events WHERE user_id=? GROUP BY to_state ORDER BY to_state",
            user);
    String metrics =
        "count(DISTINCT a.id),count(DISTINCT a.id) FILTER(WHERE e.to_state='REPLIED'),count(DISTINCT a.id) FILTER(WHERE e.to_state='INTERVIEW'),count(DISTINCT a.id) FILTER(WHERE e.to_state='OFFER')";
    var sources =
        performance(
            "SELECT s.connector,"
                + metrics
                + " FROM app.job_sources s LEFT JOIN app.applications a ON a.job_id=s.job_id AND a.user_id=s.user_id LEFT JOIN app.application_events e ON e.application_id=a.id AND e.user_id=a.user_id WHERE s.user_id=? GROUP BY s.connector ORDER BY count(DISTINCT a.id) DESC",
            user);
    var resumes =
        performance(
            "SELECT v.resume_version_id::text,"
                + metrics
                + " FROM app.outreach_versions v JOIN app.outreach_messages m ON m.current_version_id=v.id AND m.user_id=v.user_id LEFT JOIN app.applications a ON a.job_id=m.job_id AND a.user_id=m.user_id LEFT JOIN app.application_events e ON e.application_id=a.id AND e.user_id=a.user_id WHERE v.user_id=? AND v.resume_version_id IS NOT NULL AND m.state='SENT' GROUP BY v.resume_version_id ORDER BY count(DISTINCT a.id) DESC",
            user);
    var outreach =
        counts(
            "SELECT channel || ':' || state,count(*) FROM app.outreach_messages WHERE user_id=? GROUP BY channel,state ORDER BY channel,state",
            user);
    var usage =
        rows.jdbc.queryForMap(
            "SELECT count(*) FILTER(WHERE NOT cached) requests,count(*) FILTER(WHERE cached) hits,COALESCE(sum(input_tokens),0) input,COALESCE(sum(output_tokens),0) output,sum(estimated_cost) cost FROM app.ai_usage WHERE user_id=?",
            user);
    return new Ports.AnalyticsSummary(
        lifecycle,
        sources,
        resumes,
        outreach,
        ((Number) usage.get("requests")).longValue(),
        ((Number) usage.get("hits")).longValue(),
        ((Number) usage.get("input")).longValue(),
        ((Number) usage.get("output")).longValue(),
        usage.get("cost") == null ? null : ((Number) usage.get("cost")).doubleValue());
  }

  public List<Map<String, Object>> activity(UUID user, int limit) {
    return rows.jdbc.query(
        "SELECT id,action,resource_type,resource_id,metadata,at FROM app.audit_events WHERE user_id=? ORDER BY at DESC LIMIT ?",
        (r, n) -> {
          Map<String, Object> event = new LinkedHashMap<>();
          event.put("id", r.getObject("id"));
          event.put("action", r.getString("action"));
          event.put("resourceType", r.getString("resource_type"));
          event.put("resourceId", r.getObject("resource_id"));
          event.put("metadata", rows.json.readTree(r.getString("metadata")));
          event.put("at", JsonRows.instant(r, "at"));
          return event;
        },
        user,
        Math.max(1, Math.min(100, limit)));
  }
}
