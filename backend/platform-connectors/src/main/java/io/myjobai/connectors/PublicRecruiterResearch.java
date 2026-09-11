package io.myjobai.connectors;

import io.myjobai.application.Ports;
import io.myjobai.domain.*;
import org.jsoup.Jsoup;
import java.util.*;

public final class PublicRecruiterResearch implements Ports.RecruiterResearch {
    private final Ports.WebResearch research;
    public PublicRecruiterResearch(Ports.WebResearch research){this.research=research;}
    public List<Ports.ContactEvidence> find(Opportunity.Job job){
        var result=new ArrayList<Ports.ContactEvidence>();var seen=new HashSet<String>();
        for(var page:research.search(job.company()+" "+job.title()+" recruiter hiring contact")){
            String html=research.publicPage(page.url());var document=Jsoup.parse(html,page.url());
            // A public search snippet alone is not proof. Only actual links on the retrieved page are recorded.
            for(var link:document.select("a[href]")){
                String href=link.attr("href"),label=link.text().strip();if(label.isBlank()||label.length()>200)continue;
                if(href.startsWith("mailto:")){String value=href.substring(7).split("\\?",2)[0];try{value=Checks.email(value);if(seen.add(value))result.add(new Ports.ContactEvidence(label,value,Contact.Type.EMAIL,page.url()));}catch(DomainException ignored){/* Invalid addresses are not evidence. */}}
                else if(href.matches("https://(?:[a-z]+\\.)?linkedin\\.com/in/[^?#]+.*")&&seen.add(href))result.add(new Ports.ContactEvidence(label,href,Contact.Type.LINKEDIN,page.url()));
                if(result.size()>=10)return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }
}
