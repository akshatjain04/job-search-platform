package io.myjobai.domain;

import java.util.*;

public final class Compatibility {
    private Compatibility() {}
    public static Resume.Scores evaluate(Resume.Structured resume, Opportunity.Job job, String extractedPdfText, String extractedDocxText) {
        String resumeText = resume.sections().stream().flatMap(s -> s.bullets().stream()).map(Resume.Bullet::text).reduce(resume.identity().name()+" "+resume.identity().email()+" "+String.join(" ",resume.skills()),(a,b)->a+" "+b);
        var expected = tokens(resumeText); var pdf = tokens(extractedPdfText); var docx = tokens(extractedDocxText);
        double parsing = expected.isEmpty()?0:100.0*Math.min(intersection(expected,pdf),intersection(expected,docx))/expected.size();
        Set<String> skills = new HashSet<>(); resume.skills().forEach(s->skills.add(Normalization.text(s)));
        long aligned = job.skills().stream().filter(s->skills.contains(Normalization.text(s))).count();
        double coverage = job.skills().isEmpty()?0:1.0*aligned/job.skills().size();
        String normalized = Normalization.text(resumeText);
        double experience = job.skills().isEmpty()?0:job.skills().stream().filter(s->resume.sections().stream().flatMap(v->v.bullets().stream()).anyMatch(b->Normalization.text(b.text()).contains(Normalization.text(s)))).count()/(double)job.skills().size();
        double keywords = job.skills().isEmpty()?0:job.skills().stream().filter(s->normalized.contains(Normalization.text(s))).count()/(double)job.skills().size();
        var dimensions = Map.of("requirementCoverage",30*coverage,"skillsAlignment",20*coverage,"experienceEvidence",20*experience,"parseability",15*parsing/100,"keywordCoverage",10*keywords,"formatting",resume.sections().isEmpty()?0.0:5.0);
        double total = Math.round(dimensions.values().stream().mapToDouble(Double::doubleValue).sum()*100)/100.0;
        var explanation = new ArrayList<String>(); explanation.add(aligned+" of "+job.skills().size()+" required skills covered"); explanation.add("Experience uses deterministic keyword evidence, not a claim of external ATS semantic accuracy");
        if (job.skills().isEmpty()) explanation.add("Structured requirements missing; review the job before recommendation");
        if (parsing<95) explanation.add("Rendered text extraction lost source content");
        if (total<80) explanation.add("Compatibility below 80; missing facts cannot be fabricated to pass");
        return new Resume.Scores(total,Math.round(parsing*100)/100.0,dimensions,List.copyOf(explanation),total>=80&&parsing>=95);
    }
    private static Set<String> tokens(String text) { return new HashSet<>(Arrays.asList(Normalization.text(text).split("\\s+"))); }
    private static long intersection(Set<String> a,Set<String> b) { return a.stream().filter(b::contains).count(); }
}
