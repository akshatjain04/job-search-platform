package io.myjobai.domain;

import java.util.*;
import java.util.regex.Pattern;

/** Lossless section extraction for review. No extracted line is automatically a verified fact. */
public final class ResumeImport {
  private ResumeImport() {}

  public record SourceLine(int lineNumber, String text) {}

  public record Section(String kind, String sourceHeading, List<SourceLine> lines) {}

  public record Parsed(
      String status,
      List<Section> sections,
      List<String> publishedEmails,
      List<String> listedSkills) {}

  private static final Map<String, String> HEADINGS =
      Map.ofEntries(
          Map.entry("experience", "WORK_HISTORY"),
          Map.entry("work experience", "WORK_HISTORY"),
          Map.entry("professional experience", "WORK_HISTORY"),
          Map.entry("employment history", "WORK_HISTORY"),
          Map.entry("projects", "PROJECTS"),
          Map.entry("personal projects", "PROJECTS"),
          Map.entry("education", "EDUCATION"),
          Map.entry("academic qualifications", "EDUCATION"),
          Map.entry("skills", "SKILLS"),
          Map.entry("technical skills", "SKILLS"),
          Map.entry("certifications", "CERTIFICATIONS"),
          Map.entry("achievements", "ACHIEVEMENTS"),
          Map.entry("awards", "ACHIEVEMENTS"),
          Map.entry("summary", "SUMMARY"),
          Map.entry("professional summary", "SUMMARY"));

  public static Parsed parse(String text) {
    var sections = new ArrayList<Section>();
    var lines = new ArrayList<SourceLine>();
    var emails = new LinkedHashSet<String>();
    var skills = new LinkedHashSet<String>();
    String kind = "IDENTITY", heading = "Opening lines";
    var source = text.split("\\R", -1);
    for (int i = 0; i < source.length; i++) {
      String line = source[i].strip();
      if (line.isBlank()) continue;
      String recognized = HEADINGS.get(line.toLowerCase(Locale.ROOT).replaceAll(":$", "").strip());
      if (recognized != null) {
        if (!lines.isEmpty()) sections.add(new Section(kind, heading, List.copyOf(lines)));
        lines.clear();
        kind = recognized;
        heading = line;
      }
      lines.add(new SourceLine(i + 1, line));
      var found =
          Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
              .matcher(line);
      while (found.find()) emails.add(found.group());
      if (kind.equals("SKILLS") && recognized == null)
        for (String skill : line.split("[,;|]")) {
          String value = skill.strip();
          if (!value.isBlank() && value.length() <= 100) skills.add(value);
        }
    }
    if (!lines.isEmpty()) sections.add(new Section(kind, heading, List.copyOf(lines)));
    return new Parsed(
        "REQUIRES_REVIEW", List.copyOf(sections), List.copyOf(emails), List.copyOf(skills));
  }
}
