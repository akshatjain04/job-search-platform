package io.myjobai.persistence;

import org.springframework.jdbc.core.*;
import tools.jackson.databind.json.JsonMapper;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class JsonRows {
    public final JdbcTemplate jdbc;
    public final JsonMapper json;
    public JsonRows(JdbcTemplate jdbc,JsonMapper json) { this.jdbc=jdbc;this.json=json; }
    public String write(Object value) { return json.writeValueAsString(value); }
    public <T> T read(String data,Class<T> type) { return json.readValue(data,type); }
    public <T> List<T> list(String sql,Class<T> type,Object... args) { return jdbc.query(sql,(r,n)->read(r.getString("data"),type),args); }
    public <T> Optional<T> one(String sql,Class<T> type,Object... args) { return list(sql,type,args).stream().findFirst(); }
    public static Timestamp timestamp(Instant value) { return value==null?null:Timestamp.from(value); }
    public static Instant instant(ResultSet rs,String name) throws SQLException { Timestamp value=rs.getTimestamp(name);return value==null?null:value.toInstant(); }
    public static UUID uuid(ResultSet rs,String name) throws SQLException { return rs.getObject(name,UUID.class); }
}
