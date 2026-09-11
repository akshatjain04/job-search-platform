package io.myjobai.connectors;

import io.myjobai.application.Ports;
import io.myjobai.domain.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.function.Function;

public abstract class FeedConnector implements Ports.JobSourceConnector {
    protected final Function<String,String> fetch;protected final JsonMapper json;protected final Clock clock;
    protected FeedConnector(Function<String,String> fetch,JsonMapper json,Clock clock){this.fetch=fetch;this.json=json;this.clock=clock;}
    protected abstract String endpoint(String board);
    protected abstract List<Ports.DiscoveredJob> parse(String board,JsonNode payload);
    public List<Ports.DiscoveredJob> discoverJobs(Ports.DiscoveryCriteria criteria){
        if(!criteria.board().matches("[A-Za-z0-9_-]{1,100}"))throw DomainException.invalid("ATS board must be its public board identifier, not a URL");
        if(criteria.limit()<1||criteria.limit()>500)throw DomainException.invalid("Discovery limit must be 1..500");
        return parse(criteria.board(),json.readTree(fetch.apply(endpoint(criteria.board())))).stream().filter(j->criteria.role()==null||criteria.role().isBlank()||Normalization.text(j.title()).contains(Normalization.text(criteria.role()))).limit(criteria.limit()).toList();
    }
    public Ports.DiscoveredJob fetchJob(String board,String id){return discoverJobs(new Ports.DiscoveryCriteria(board,"",500)).stream().filter(j->j.externalId().equals(id)).findFirst().orElseThrow(DomainException::missing);}
    public Ports.SourceCapabilities capabilities(){return new Ports.SourceCapabilities(true,true,false,"Public ATS job feed; no social crawling");}
    protected Ports.DiscoveredJob job(String id,String title,String company,String location,String html,boolean remote,String employment,String url,String date){String description=PageExtraction.sanitize(html);return new Ports.DiscoveredJob(id,title,company,location,description,Opportunity.Kind.JOB_POSTING,remote,employment,null,null,"",null,PageExtraction.skills(description),PageExtraction.parseDate(date,clock.instant()),url);}
}
