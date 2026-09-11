package io.myjobai.connectors;

import io.myjobai.application.Ports;
import java.util.*;

public final class CompanyCareerConnector implements Ports.JobSourceConnector {
    private final SafeWebClient http;private final PageExtraction extraction;
    public CompanyCareerConnector(SafeWebClient http,PageExtraction extraction){this.http=http;this.extraction=extraction;}
    public String key(){return "company-career";}
    public List<Ports.DiscoveredJob> discoverJobs(Ports.DiscoveryCriteria criteria){return List.of(extraction.careerPage(criteria.board(),http.get(criteria.board())));}
    public Ports.DiscoveredJob fetchJob(String board,String externalId){return discoverJobs(new Ports.DiscoveryCriteria(board,"",1)).getFirst();}
    public Ports.SourceCapabilities capabilities(){return new Ports.SourceCapabilities(false,true,false,"User-configured public JobPosting JSON-LD page");}
}
