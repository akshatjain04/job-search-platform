package io.myjobai.domain;

import java.time.Instant;
import java.util.*;

public final class Resume {
  private Resume() {}

  public record Bullet(UUID factId, String text) {}

  public record Section(String heading, List<Bullet> bullets) {
    public Section {
      heading = Checks.text(heading, "Section heading", 600);
      bullets = Checks.list(bullets);
    }
  }

  public record Structured(
      Candidate.Identity identity,
      List<Section> sections,
      List<String> skills,
      List<Candidate.Education> education) {
    public Structured(Candidate.Identity identity, List<Section> sections, List<String> skills) {
      this(identity, sections, skills, List.of());
    }

    public Structured {
      Checks.required(identity);
      sections = Checks.list(sections);
      skills = Checks.list(skills);
      education = Checks.list(education);
    }
  }

  public record Base(
      UUID id,
      UUID userId,
      String filename,
      String objectKey,
      String contentHash,
      String extractedText,
      Instant createdAt,
      ResumeImport.Parsed parsed) {
    public Base(
        UUID id,
        UUID userId,
        String filename,
        String objectKey,
        String contentHash,
        String extractedText,
        Instant createdAt) {
      this(id, userId, filename, objectKey, contentHash, extractedText, createdAt, null);
    }

    public Base {
      if (parsed == null) parsed = ResumeImport.parse(extractedText);
    }
  }

  public record Scores(
      double compatibility,
      double parsing,
      Map<String, Double> dimensions,
      List<String> explanations,
      boolean recommended) {}

  public record Version(
      UUID id,
      UUID userId,
      UUID resumeId,
      UUID jobId,
      Structured content,
      String pdfKey,
      String docxKey,
      String pdfHash,
      String docxHash,
      List<Scores> scoringHistory,
      Instant createdAt) {}

  public static void validateGrounding(
      Structured content, Candidate.Profile profile, List<Candidate.Fact> facts) {
    if (!content.identity().equals(profile.identity()))
      throw DomainException.invalid("Generated identity differs from the candidate profile");
    if (!content.education().equals(profile.education()))
      throw DomainException.invalid("Generated education differs from the candidate profile");
    var verified = new HashMap<UUID, Candidate.Fact>();
    facts.stream()
        .filter(f -> f.verified() && f.userId().equals(profile.userId()))
        .forEach(f -> verified.put(f.id(), f));
    var allowedSkills = new HashSet<String>();
    verified
        .values()
        .forEach(f -> f.skills().forEach(s -> allowedSkills.add(Normalization.text(s))));
    for (String skill : content.skills())
      if (!allowedSkills.contains(Normalization.text(skill)))
        throw DomainException.invalid("Unsupported generated skill: " + skill);
    for (var section : content.sections())
      for (var bullet : section.bullets()) {
        var fact = verified.get(bullet.factId());
        if (fact == null)
          throw DomainException.invalid("Resume references an unverified or foreign fact");
        // Selection and whitespace normalization are provable; arbitrary semantic rewrites are not.
        if (!Normalization.text(bullet.text()).equals(Normalization.text(fact.statement())))
          throw DomainException.invalid(
              "Generated claim is not supported verbatim by its verified source fact");
        String expectedHeading = fact.resumeHeading();
        if (!section.heading().equals(expectedHeading))
          throw DomainException.invalid("Generated employment/context heading is unsupported");
      }
  }
}
