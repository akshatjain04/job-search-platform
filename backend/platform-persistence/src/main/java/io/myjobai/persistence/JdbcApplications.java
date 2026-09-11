package io.myjobai.persistence;

import io.myjobai.application.Ports;
import io.myjobai.domain.*;
import org.springframework.jdbc.core.RowMapper;
import java.time.Instant;
import java.util.*;
import static io.myjobai.persistence.JsonRows.*;

public final class JdbcApplications implements Ports.Applications {
    private final JsonRows rows;
    private final RowMapper<ApplicationLifecycle.Application> mapper=(r,n)->new ApplicationLifecycle.Application(uuid(r,"id"),uuid(r,"user_id"),uuid(r,"job_id"),ApplicationLifecycle.State.valueOf(r.getString("state")),instant(r,"created_at"),instant(r,"updated_at"));
    public JdbcApplications(JsonRows rows){this.rows=rows;}
    public ApplicationLifecycle.Application create(UUID user,UUID job,Instant now){
        UUID id=UUID.randomUUID();
        int inserted=rows.jdbc.update("INSERT INTO app.applications(id,user_id,job_id,state,created_at,updated_at) VALUES (?,?,?,'DISCOVERED',?,?) ON CONFLICT(user_id,job_id) DO NOTHING",id,user,job,timestamp(now),timestamp(now));
        if(inserted==1)rows.jdbc.update("INSERT INTO app.application_events(id,user_id,application_id,to_state,at) VALUES (?,?,?,'DISCOVERED',?)",UUID.randomUUID(),user,id,timestamp(now));
        return forJob(user,job).orElseThrow(DomainException::missing);
    }
    public Optional<ApplicationLifecycle.Application> find(UUID user,UUID id,boolean lock){return rows.jdbc.query("SELECT * FROM app.applications WHERE user_id=? AND id=?"+(lock?" FOR UPDATE":""),mapper,user,id).stream().findFirst();}
    public Optional<ApplicationLifecycle.Application> forJob(UUID user,UUID job){return rows.jdbc.query("SELECT * FROM app.applications WHERE user_id=? AND job_id=?",mapper,user,job).stream().findFirst();}
    public List<ApplicationLifecycle.Application> list(UUID user){return rows.jdbc.query("SELECT * FROM app.applications WHERE user_id=? ORDER BY updated_at DESC",mapper,user);}
    public void transition(ApplicationLifecycle.Application current,ApplicationLifecycle.State next,String note,Instant now){
        int updated=rows.jdbc.update("UPDATE app.applications SET state=?,updated_at=? WHERE user_id=? AND id=? AND state=?",next.name(),timestamp(now),current.userId(),current.id(),current.state().name());
        if(updated!=1)throw DomainException.conflict("Application was changed concurrently");
        rows.jdbc.update("INSERT INTO app.application_events(id,user_id,application_id,from_state,to_state,note,at) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),current.userId(),current.id(),current.state().name(),next.name(),note,timestamp(now));
    }
    public List<ApplicationLifecycle.Event> events(UUID user,UUID application){return rows.jdbc.query("SELECT * FROM app.application_events WHERE user_id=? AND application_id=? ORDER BY at,id",(r,n)->new ApplicationLifecycle.Event(uuid(r,"id"),uuid(r,"user_id"),uuid(r,"application_id"),r.getString("from_state")==null?null:ApplicationLifecycle.State.valueOf(r.getString("from_state")),ApplicationLifecycle.State.valueOf(r.getString("to_state")),r.getString("note"),instant(r,"at")),user,application);}
}
