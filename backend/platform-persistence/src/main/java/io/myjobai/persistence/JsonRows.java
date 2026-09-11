package io.myjobai.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import tools.jackson.databind.json.JsonMapper;

public final class JsonRows {
  public final JdbcTemplate jdbc;
  public final JsonMapper json;

  public JsonRows(JdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public String write(Object value) {
    return json.writeValueAsString(value);
  }

  public String canonical(Object value) {
    return write(sorted(json.convertValue(value, Object.class)));
  }

  private static Object sorted(Object value) {
    if (value instanceof Map<?, ?> map) {
      var ordered = new TreeMap<String, Object>();
      map.forEach((k, v) -> ordered.put(k.toString(), sorted(v)));
      return ordered;
    }
    if (value instanceof List<?> list) return list.stream().map(JsonRows::sorted).toList();
    return value;
  }

  public <T> T read(String data, Class<T> type) {
    return json.readValue(data, type);
  }

  public <T> List<T> list(String sql, Class<T> type, Object... args) {
    return jdbc.query(sql, (r, n) -> read(r.getString("data"), type), args);
  }

  public <T> Optional<T> one(String sql, Class<T> type, Object... args) {
    return list(sql, type, args).stream().findFirst();
  }

  public static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  public static Instant instant(ResultSet rs, String name) throws SQLException {
    Timestamp value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }

  public static UUID uuid(ResultSet rs, String name) throws SQLException {
    return rs.getObject(name, UUID.class);
  }
}
