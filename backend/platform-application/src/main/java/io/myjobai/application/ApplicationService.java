package io.myjobai.application;

import io.myjobai.domain.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

public class ApplicationService {
    private final Ports.Applications applications; private final JobService jobs; private final Ports.Audit audit; private final Clock clock;
    public ApplicationService(Ports.Applications applications,JobService jobs,Ports.Audit audit,Clock clock) { this.applications=applications;this.jobs=jobs;this.audit=audit;this.clock=clock; }
    @Transactional public ApplicationLifecycle.Application create(UUID user,UUID job) { jobs.get(user,job); return applications.create(user,job,clock.instant()); }
    public List<ApplicationLifecycle.Application> list(UUID user) { return applications.list(user); }
    public List<ApplicationLifecycle.Event> events(UUID user,UUID id) { applications.find(user,id,false).orElseThrow(DomainException::missing); return applications.events(user,id); }
    @Transactional public ApplicationLifecycle.Application update(UUID user,UUID id,ApplicationLifecycle.State state,String note) {
        var current=applications.find(user,id,true).orElseThrow(DomainException::missing);
        ApplicationLifecycle.requireTransition(current.state(),state);
        applications.transition(current,state,Checks.optional(note,2000),clock.instant());
        audit.record(user,"APPLICATION_TRANSITION","application",id,Map.of("from",current.state().name(),"to",state.name()));
        return applications.find(user,id,false).orElseThrow(DomainException::missing);
    }
}
