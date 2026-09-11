package io.myjobai.application;

import io.myjobai.domain.Opportunity;
import java.util.UUID;

public final class CaptureService {
  private final Ports.PageCaptures extractor;
  private final JobService jobs;

  public CaptureService(Ports.PageCaptures extractor, JobService jobs) {
    this.extractor = extractor;
    this.jobs = jobs;
  }

  public Opportunity.Job capture(UUID user, Ports.CaptureInput input) {
    return jobs.ingest(user, extractor.extract(input), "browser-capture");
  }
}
