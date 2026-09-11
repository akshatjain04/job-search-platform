package io.myjobai.domain;

import java.time.Instant;
import java.util.*;

public final class Opportunity {
  private Opportunity() {}

  public enum Kind {
    JOB_POSTING,
    HIRING_POST,
    REFERRAL_POST
  }

  public record Job(
      UUID id,
      UUID userId,
      String title,
      String company,
      String location,
      String description,
      Kind kind,
      boolean remote,
      String employmentType,
      Integer salaryMin,
      Integer salaryMax,
      String currency,
      Integer experienceYears,
      List<String> skills,
      Instant postedAt,
      String canonicalUrl) {
    public Job {
      Checks.required(id);
      Checks.required(userId);
      title = Checks.text(title, "Job title", 300);
      company = Checks.text(company, "Company", 200);
      location = Checks.optional(location, 200);
      description = Checks.text(description, "Job content", 60000);
      Checks.required(kind);
      employmentType = Checks.optional(employmentType, 60);
      currency = Checks.optional(currency, 3);
      skills = Checks.list(skills);
      canonicalUrl = Checks.publicUrl(canonicalUrl);
      Checks.required(postedAt);
      if (salaryMin != null && salaryMin < 0
          || salaryMax != null && salaryMax < 0
          || salaryMin != null && salaryMax != null && salaryMax < salaryMin)
        throw DomainException.invalid("Invalid salary range");
      if (experienceYears != null && (experienceYears < 0 || experienceYears > 60))
        throw DomainException.invalid("Invalid experience requirement");
    }
  }

  public record Source(
      UUID id,
      UUID userId,
      UUID jobId,
      String connector,
      String externalId,
      String url,
      String contentHash,
      Kind kind,
      Instant observedAt) {}

  public record Match(
      UUID jobId,
      UUID userId,
      double score,
      boolean eligible,
      List<String> matchedSkills,
      List<String> missingSkills,
      List<String> relevantExperience,
      Map<String, Double> dimensions,
      List<String> explanation,
      Instant calculatedAt) {}

  public record Search(
      String role,
      String company,
      String location,
      Integer salaryMin,
      Integer experienceMax,
      Boolean remote,
      Instant since,
      String source,
      Boolean referral,
      Boolean hasRecruiter,
      Double minScore,
      int page,
      int size) {
    public Search {
      if (page < 0 || page > 10000 || size < 1 || size > 100)
        throw DomainException.invalid("Page must be >=0 and size between 1 and 100");
    }

    public static Search first() {
      return new Search(null, null, null, null, null, null, null, null, null, null, null, 0, 25);
    }
  }

  public record Page<T>(List<T> items, int page, int size, long total) {}
}
