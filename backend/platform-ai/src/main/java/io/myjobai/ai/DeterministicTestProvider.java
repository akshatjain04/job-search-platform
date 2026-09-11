package io.myjobai.ai;

import io.myjobai.application.Ports;
import io.myjobai.domain.Candidate;
import java.util.*;

/**
 * Explicit non-production acceptance-test adapter. Runtime configuration must reject it in
 * production.
 */
public final class DeterministicTestProvider implements Ports.LlmProvider {
  public String key() {
    return "test";
  }

  public Ports.Completion generate(Ports.LlmRequest request) {
    if (!Set.of("resume.select", "outreach.select").contains(request.task()))
      throw new IllegalArgumentException("Unknown deterministic test task: " + request.task());
    var facts = (List<?>) request.untrustedData().get("facts");
    var ids = facts.stream().map(f -> ((Candidate.Fact) f).id().toString()).limit(30).toList();
    var output = Map.<String, Object>of("factIds", ids);
    JsonSchemaValidator.validate(request.schema(), output);
    return new Ports.Completion(output, new Ports.Usage("test", "deterministic", 0, 0, 0, 0.0));
  }
}
