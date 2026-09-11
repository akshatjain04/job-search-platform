package io.myjobai.persistence;

import io.myjobai.application.Ports;
import io.myjobai.domain.Candidate;
import java.util.*;

public final class JdbcProfiles implements Ports.Profiles {
    private final JsonRows rows;
    public JdbcProfiles(JsonRows rows) { this.rows=rows; }
    public Optional<Candidate.Profile> find(UUID user) { return rows.one("SELECT data FROM app.candidate_profiles WHERE user_id=?",Candidate.Profile.class,user); }
    public void save(Candidate.Profile profile) { rows.jdbc.update("INSERT INTO app.candidate_profiles(user_id,data) VALUES (?,?::jsonb) ON CONFLICT(user_id) DO UPDATE SET data=excluded.data,updated_at=now()",profile.userId(),rows.write(profile)); }
    public List<Candidate.Fact> facts(UUID user) { return rows.list("SELECT data FROM app.experience_facts WHERE user_id=? ORDER BY created_at,id",Candidate.Fact.class,user); }
    public void saveFact(Candidate.Fact fact) { rows.jdbc.update("INSERT INTO app.experience_facts(id,user_id,verified,data) VALUES (?,?,?,?::jsonb)",fact.id(),fact.userId(),fact.verified(),rows.write(fact)); }
}
