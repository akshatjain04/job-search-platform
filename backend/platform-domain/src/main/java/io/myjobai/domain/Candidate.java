package io.myjobai.domain;

import java.time.LocalDate;
import java.util.*;

public final class Candidate {
    private Candidate() {}
    public record Identity(String name, String email, String phone, String location, List<String> links) {
        public Identity { name = Checks.text(name, "Name", 150); email = Checks.email(email); phone = Checks.optional(phone, 40); location = Checks.optional(location, 200); links = Checks.list(links); links.forEach(Checks::publicUrl); }
    }
    public record Work(String company, String role, LocalDate from, LocalDate to, String description) {}
    public record Project(String name, String description, String url, List<String> skills) {}
    public record Education(String institution, String qualification, String dates) {}
    public record Preferences(List<String> roles, List<String> excludedRoles, List<String> companies, List<String> excludedCompanies,
                              List<String> locations, boolean remoteOnly, Integer minimumSalary, String currency,
                              String workAuthorization, String noticePeriod, String tone, String resumeTemplate,
                              Map<String, String> outreachTemplates, double alertThreshold) {
        public Preferences {
            roles = Checks.list(roles); excludedRoles = Checks.list(excludedRoles); companies = Checks.list(companies); excludedCompanies = Checks.list(excludedCompanies); locations = Checks.list(locations);
            currency = Checks.optional(currency, 3); workAuthorization = Checks.optional(workAuthorization, 300); noticePeriod = Checks.optional(noticePeriod, 100);
            tone = tone == null ? "professional" : Checks.text(tone, "Tone", 100); resumeTemplate = resumeTemplate == null ? "classic" : Checks.text(resumeTemplate, "Template", 40);
            outreachTemplates = outreachTemplates == null ? Map.of() : Map.copyOf(outreachTemplates);
            if (minimumSalary != null && minimumSalary < 0) throw DomainException.invalid("Minimum salary cannot be negative");
            if (alertThreshold < 0 || alertThreshold > 100) throw DomainException.invalid("Alert threshold must be 0 to 100");
        }
        public static Preferences defaults() { return new Preferences(null,null,null,null,null,false,null,"","","","professional","classic",null,80); }
    }
    public record Profile(UUID userId, Identity identity, List<Work> workHistory, List<Project> projects, List<Education> education,
                          List<String> skills, List<String> certifications, List<String> achievements, Preferences preferences) {
        public Profile {
            Objects.requireNonNull(userId); Objects.requireNonNull(identity);
            workHistory = Checks.list(workHistory); projects = Checks.list(projects); education = Checks.list(education); skills = Checks.list(skills);
            certifications = Checks.list(certifications); achievements = Checks.list(achievements); preferences = preferences == null ? Preferences.defaults() : preferences;
            if (skills.size() > 200 || workHistory.size() > 60 || projects.size() > 100) throw DomainException.invalid("Profile exceeds supported limits");
        }
    }
    public record Fact(UUID id, UUID userId, String company, String context, String statement, List<String> skills,
                       Map<String,String> metrics, LocalDate from, LocalDate to, String provenance, boolean verified) {
        public Fact {
            Objects.requireNonNull(id); Objects.requireNonNull(userId); company = Checks.optional(company,200); context = Checks.optional(context,200);
            statement = Checks.text(statement,"Verified fact",3000); skills = Checks.list(skills); metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
            provenance = Checks.text(provenance,"Provenance",2000);
            if (from != null && to != null && to.isBefore(from)) throw DomainException.invalid("Fact end date precedes start date");
        }
    }
}
