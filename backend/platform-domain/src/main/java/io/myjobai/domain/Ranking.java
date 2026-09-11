package io.myjobai.domain;

import java.time.*;
import java.util.*;

public final class Ranking {
    public record Weights(double candidate, double freshness, double referral, double reachability, double company, double salary) {
        public Weights { var values = List.of(candidate,freshness,referral,reachability,company,salary); if (values.stream().anyMatch(v -> !Double.isFinite(v) || v < 0) || Math.abs(values.stream().mapToDouble(Double::doubleValue).sum()-1)>0.00001) throw DomainException.invalid("Ranking weights must be nonnegative and sum to 1"); }
        public static Weights defaults() { return new Weights(.40,.20,.15,.10,.10,.05); }
    }
    private final Weights weights;
    private final Clock clock;
    public Ranking(Weights weights, Clock clock) { this.weights = weights; this.clock = clock; }
    public Opportunity.Match match(Candidate.Profile profile, List<Candidate.Fact> facts, Opportunity.Job job, boolean reachable) {
        if (!profile.userId().equals(job.userId())) throw DomainException.missing();
        var prefs = profile.preferences(); var reasons = new ArrayList<String>();
        boolean eligible = true;
        if (prefs.remoteOnly() && !job.remote()) { eligible = false; reasons.add("Remote-only preference not met"); }
        if (prefs.excludedCompanies().stream().anyMatch(v -> Normalization.text(v).equals(Normalization.text(job.company())))) { eligible = false; reasons.add("Company is excluded"); }
        if (prefs.excludedRoles().stream().anyMatch(v -> Normalization.text(job.title()).contains(Normalization.text(v)))) { eligible = false; reasons.add("Role is excluded"); }
        boolean salaryKnown = prefs.minimumSalary() != null && job.salaryMax() != null && !prefs.currency().isBlank() && prefs.currency().equalsIgnoreCase(job.currency());
        if (salaryKnown && job.salaryMax() < prefs.minimumSalary()) { eligible = false; reasons.add("Salary ceiling below requested minimum"); }
        boolean location = prefs.locations().isEmpty() || job.remote() || prefs.locations().stream().anyMatch(v -> Normalization.text(job.location()).contains(Normalization.text(v)));
        if (!location) reasons.add("Location preference differs");
        Set<String> verifiedSkills = new HashSet<>();
        facts.stream().filter(f -> f.verified() && f.userId().equals(profile.userId())).flatMap(f -> f.skills().stream()).map(Normalization::text).forEach(verifiedSkills::add);
        var matched = job.skills().stream().filter(s -> verifiedSkills.contains(Normalization.text(s))).toList();
        var missing = job.skills().stream().filter(s -> !verifiedSkills.contains(Normalization.text(s))).toList();
        double candidate = (job.skills().isEmpty() ? 50 : 100.0*matched.size()/job.skills().size()) * (location ? 1 : .75);
        double ageDays = Math.max(0,Duration.between(job.postedAt(),clock.instant()).toHours()/24.0);
        double freshness = 100*Math.exp(-ageDays/30.0);
        double referral = switch (job.kind()) { case REFERRAL_POST -> 100; case HIRING_POST -> 65; case JOB_POSTING -> 0; };
        double company = prefs.companies().isEmpty() ? 50 : prefs.companies().stream().anyMatch(v -> Normalization.text(v).equals(Normalization.text(job.company()))) ? 100 : 0;
        double salary = salaryKnown ? job.salaryMax() >= prefs.minimumSalary() ? 100 : 0 : 50;
        double reachability = reachable ? 100 : 0;
        double score = eligible ? candidate*weights.candidate + freshness*weights.freshness + referral*weights.referral + reachability*weights.reachability + company*weights.company + salary*weights.salary : 0;
        var dimensions = Map.of("candidate",candidate,"freshness",freshness,"referral",referral,"reachability",reachability,"company",company,"salary",salary,"location",location?100.0:0.0);
        reasons.add(matched.size()+" of "+job.skills().size()+" required skills supported by verified facts");
        if (!salaryKnown) reasons.add("Salary comparison unavailable; neutral score");
        if (job.skills().isEmpty()) reasons.add("No structured requirements available; candidate score is provisional");
        var experience = facts.stream().filter(f -> f.verified() && f.userId().equals(profile.userId()) && f.skills().stream().anyMatch(s -> matched.stream().anyMatch(m -> Normalization.text(s).equals(Normalization.text(m))))).map(Candidate.Fact::statement).toList();
        return new Opportunity.Match(job.id(),profile.userId(),Math.round(score*100.0)/100.0,eligible,matched,missing,experience,dimensions,List.copyOf(reasons),clock.instant());
    }
}
