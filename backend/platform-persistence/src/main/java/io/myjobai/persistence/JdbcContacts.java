package io.myjobai.persistence;

import io.myjobai.application.Ports;
import io.myjobai.domain.Contact;
import java.util.*;

public final class JdbcContacts implements Ports.Contacts {
    private final JsonRows rows;
    public JdbcContacts(JsonRows rows){this.rows=rows;}
    public void save(Contact.Recruiter recruiter,Contact contact,UUID job){
        rows.jdbc.update("INSERT INTO app.recruiters(id,user_id,data) VALUES (?,?,?::jsonb) ON CONFLICT(id) DO NOTHING",recruiter.id(),recruiter.userId(),rows.write(recruiter));
        rows.jdbc.update("INSERT INTO app.contact_points(id,user_id,recruiter_id,type,value,verification_status,data) VALUES (?,?,?,?,?,?,?::jsonb)",contact.id(),contact.userId(),contact.recruiterId(),contact.type().name(),contact.value(),contact.verificationStatus().name(),rows.write(contact));
        rows.jdbc.update("INSERT INTO app.job_recruiters(user_id,job_id,recruiter_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",contact.userId(),job,recruiter.id());
    }
    public Optional<Contact> find(UUID user,UUID id){return rows.one("SELECT data FROM app.contact_points WHERE user_id=? AND id=?",Contact.class,user,id);}
    public List<Contact> forJob(UUID user,UUID job){return rows.list("SELECT c.data FROM app.contact_points c JOIN app.job_recruiters j ON j.recruiter_id=c.recruiter_id AND j.user_id=c.user_id WHERE j.user_id=? AND j.job_id=? ORDER BY c.id",Contact.class,user,job);}
}
