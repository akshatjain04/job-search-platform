package io.myjobai.application;

import io.myjobai.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

public class ResumeService {
  private final Ports.Resumes resumes;
  private final Ports.Profiles profiles;
  private final Ports.ObjectStorage storage;
  private final Ports.ResumeParser parser;
  private final Ports.Outbox outbox;
  private final Ports.Audit audit;
  private final JobService jobs;
  private final Clock clock;

  public ResumeService(
      Ports.Resumes resumes,
      Ports.Profiles profiles,
      Ports.ObjectStorage storage,
      Ports.ResumeParser parser,
      Ports.Outbox outbox,
      Ports.Audit audit,
      JobService jobs,
      Clock clock) {
    this.resumes = resumes;
    this.profiles = profiles;
    this.storage = storage;
    this.parser = parser;
    this.outbox = outbox;
    this.audit = audit;
    this.jobs = jobs;
    this.clock = clock;
  }

  @Transactional
  public Resume.Base upload(UUID user, String filename, String mediaType, byte[] bytes) {
    if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
      throw DomainException.invalid("Resume must contain 1 byte to 10 MiB");
    if (!Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        .contains(mediaType)) throw DomainException.invalid("Upload PDF or DOCX");
    String text = parser.extract(bytes, mediaType);
    if (text.isBlank())
      throw DomainException.invalid(
          "No text could be extracted. Scanned/image-only resumes require OCR before upload.");
    UUID id = UUID.randomUUID();
    String safeName = Checks.text(filename, "Filename", 200).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
    String key =
        storage.put(
            user,
            "resumes/" + id + "/base" + (mediaType.equals("application/pdf") ? ".pdf" : ".docx"),
            bytes,
            mediaType);
    var base =
        new Resume.Base(
            id,
            user,
            safeName,
            key,
            Normalization.hash(Base64.getEncoder().encodeToString(bytes)),
            text,
            clock.instant());
    resumes.saveBase(base);
    audit.record(user, "RESUME_UPLOADED", "resume", id, Map.of("mediaType", mediaType));
    return base;
  }

  public List<Resume.Base> bases(UUID user) {
    return resumes.bases(user);
  }

  public List<Resume.Version> versions(UUID user, UUID job) {
    if (job != null) jobs.get(user, job);
    return resumes.versions(user, job);
  }

  public Resume.Version version(UUID user, UUID id) {
    return resumes.version(user, id).orElseThrow(DomainException::missing);
  }

  @Transactional
  public AsyncJob tailor(UUID user, UUID resume, UUID job, String key) {
    resumes.base(user, resume).orElseThrow(DomainException::missing);
    jobs.get(user, job);
    if (profiles.facts(user).stream().noneMatch(Candidate.Fact::verified))
      throw DomainException.invalid("Review and verify experience facts before tailoring");
    return outbox.enqueue(
        user,
        "TAILOR_RESUME",
        Map.of("resumeId", resume.toString(), "jobId", job.toString()),
        Checks.text(key, "Idempotency-Key", 100),
        clock.instant());
  }

  public byte[] download(UUID user, UUID id, String format) {
    var version = version(user, id);
    if (!Set.of("pdf", "docx").contains(format))
      throw DomainException.invalid("Format must be pdf or docx");
    return storage.get(user, format.equals("pdf") ? version.pdfKey() : version.docxKey());
  }
}
