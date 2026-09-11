package io.myjobai.persistence;

import io.myjobai.application.Ports;
import io.myjobai.domain.Resume;
import java.util.*;

public final class JdbcResumes implements Ports.Resumes {
  private final JsonRows rows;

  public JdbcResumes(JsonRows rows) {
    this.rows = rows;
  }

  public void saveBase(Resume.Base base) {
    rows.jdbc.update(
        "INSERT INTO app.resumes(id,user_id,data) VALUES (?,?,?::jsonb)",
        base.id(),
        base.userId(),
        rows.write(base));
  }

  public Optional<Resume.Base> base(UUID user, UUID id) {
    return rows.one(
        "SELECT data FROM app.resumes WHERE user_id=? AND id=?", Resume.Base.class, user, id);
  }

  public List<Resume.Base> bases(UUID user) {
    return rows.list(
        "SELECT data FROM app.resumes WHERE user_id=? ORDER BY created_at DESC",
        Resume.Base.class,
        user);
  }

  public void saveVersion(Resume.Version version) {
    rows.jdbc.update(
        "INSERT INTO app.resume_versions(id,user_id,resume_id,job_id,data) VALUES (?,?,?,?,?::jsonb)",
        version.id(),
        version.userId(),
        version.resumeId(),
        version.jobId(),
        rows.write(version));
  }

  public Optional<Resume.Version> version(UUID user, UUID id) {
    return rows.one(
        "SELECT data FROM app.resume_versions WHERE user_id=? AND id=?",
        Resume.Version.class,
        user,
        id);
  }

  public List<Resume.Version> versions(UUID user, UUID job) {
    return job == null
        ? rows.list(
            "SELECT data FROM app.resume_versions WHERE user_id=? ORDER BY created_at DESC",
            Resume.Version.class,
            user)
        : rows.list(
            "SELECT data FROM app.resume_versions WHERE user_id=? AND job_id=? ORDER BY created_at DESC",
            Resume.Version.class,
            user,
            job);
  }
}
