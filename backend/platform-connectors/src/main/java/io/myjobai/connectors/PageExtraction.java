package io.myjobai.connectors;

import io.myjobai.application.Ports;
import io.myjobai.domain.*;
import org.jsoup.Jsoup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

public final class PageExtraction implements Ports.PageCaptures {
    private static final List<String> SKILLS=List.of("Java","Spring Boot","Python","React","TypeScript","JavaScript","PostgreSQL","SQL","AWS","Docker","Go","Kotlin","C++","C#","Rust","Node.js","Git","CI/CD","GraphQL","REST","Azure","GCP","Machine Learning");
    private final JsonMapper json;private final Clock clock;
    public PageExtraction(JsonMapper json,Clock clock){this.json=json;this.clock=clock;}
    public Ports.DiscoveredJob extract(Ports.CaptureInput input){return capture(input.url(),input.title(),input.text(),input.company(),input.metadata()==null?null:json.valueToTree(input.metadata()));}
    public static String sanitize(String html){var document=Jsoup.parse(Checks.text(html,"Page content",200000));document.select("script,style,noscript,iframe,form,svg").remove();return document.text().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","").strip();}
    public static List<String> skills(String text){String normalized=" "+Normalization.text(text)+" ";return SKILLS.stream().filter(s->normalized.contains(" "+Normalization.text(s)+" ")).toList();}
    public Ports.DiscoveredJob capture(String url,String pageTitle,String visibleText,String companyOverride,JsonNode metadata){
        Checks.publicUrl(url);String text=sanitize(visibleText);String normalized=Normalization.text(text);
        Opportunity.Kind kind=normalized.contains("referral")||normalized.contains("refer you")?Opportunity.Kind.REFERRAL_POST:normalized.contains("hiring")?Opportunity.Kind.HIRING_POST:Opportunity.Kind.JOB_POSTING;
        JsonNode structured=findJob(metadata);
        if(structured==null&&!normalized.matches("(?s).*(job|hiring|career|referral|engineer|developer|vacanc|recruit).*"))throw DomainException.invalid("This page does not appear to describe a job, hiring or referral opportunity");
        String title=structured==null?pageTitle:structured.path("title").asText(pageTitle);
        String company=companyOverride==null||companyOverride.isBlank()?structured==null?"Unknown — review required":structured.path("hiringOrganization").path("name").asText("Unknown — review required"):companyOverride;
        String location=structured==null?"":structured.path("jobLocation").path("address").path("addressLocality").asText("");
        String description=structured!=null&&structured.has("description")?sanitize(structured.path("description").asText()):text;
        Instant date=clock.instant();if(structured!=null&&structured.has("datePosted"))date=parseDate(structured.path("datePosted").asText(),date);
        return new Ports.DiscoveredJob(Normalization.hash(Normalization.url(url)),Checks.text(title,"Page title",300),company,location,description,kind,normalized.contains("remote"),structured==null?"":structured.path("employmentType").asText(""),null,null,"",null,skills(description),date,url);
    }
    public Ports.DiscoveredJob careerPage(String url,String html){
        var document=Jsoup.parse(html);JsonNode job=null;for(var script:document.select("script[type=application/ld+json]")){try{job=findJob(json.readTree(script.data()));}catch(RuntimeException ignored){/* Other page metadata may not be valid JSON-LD. */}if(job!=null)break;}
        if(job==null)throw DomainException.invalid("Company page must publish JobPosting JSON-LD; use browser capture for other pages");
        return capture(url,document.title(),html,null,job);
    }
    private static JsonNode findJob(JsonNode node){if(node==null)return null;if(node.isObject()&&node.path("@type").asText().equals("JobPosting"))return node;if(node.isArray())for(var child:node){var result=findJob(child);if(result!=null)return result;}if(node.has("@graph"))return findJob(node.path("@graph"));return null;}
    static Instant parseDate(String raw,Instant fallback){try{return Instant.parse(raw);}catch(RuntimeException e){try{return LocalDate.parse(raw.substring(0,10)).atStartOfDay(ZoneOffset.UTC).toInstant();}catch(RuntimeException ignored){return fallback;}}}
}
