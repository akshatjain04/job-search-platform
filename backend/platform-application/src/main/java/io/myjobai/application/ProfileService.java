package io.myjobai.application;

import io.myjobai.domain.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public class ProfileService {
    private final Ports.Profiles profiles; private final Ports.Audit audit;
    public ProfileService(Ports.Profiles profiles,Ports.Audit audit) { this.profiles=profiles; this.audit=audit; }
    public Candidate.Profile get(UUID user) { return profiles.find(user).orElseThrow(DomainException::missing); }
    @Transactional public Candidate.Profile save(UUID user,Candidate.Profile input) {
        var profile=new Candidate.Profile(user,input.identity(),input.workHistory(),input.projects(),input.education(),input.skills(),input.certifications(),input.achievements(),input.preferences());
        profiles.save(profile); audit.record(user,"PROFILE_UPDATED","profile",user,Map.of()); return profile;
    }
    public List<Candidate.Fact> facts(UUID user) { return profiles.facts(user); }
    @Transactional public Candidate.Fact addFact(UUID user,Candidate.Fact input) {
        var fact=new Candidate.Fact(UUID.randomUUID(),user,input.company(),input.context(),input.statement(),input.skills(),input.metrics(),input.from(),input.to(),input.provenance(),input.verified());
        profiles.saveFact(fact); audit.record(user,fact.verified()?"FACT_VERIFIED":"FACT_IMPORTED","fact",fact.id(),Map.of("provenance",fact.provenance())); return fact;
    }
}
