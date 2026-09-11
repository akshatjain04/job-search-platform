package io.myjobai.api;

import io.myjobai.application.*;
import io.myjobai.domain.*;
import io.myjobai.runtime.IdentityService.Principal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {
  private final ApplicationService applications;
  private final GenerationService generation;
  private final ApprovalService approvals;
  private final DiscoveryService discovery;
  private final Ports.Outbox outbox;

  public WorkflowController(
      ApplicationService applications,
      GenerationService generation,
      ApprovalService approvals,
      DiscoveryService discovery,
      Ports.Outbox outbox) {
    this.applications = applications;
    this.generation = generation;
    this.approvals = approvals;
    this.discovery = discovery;
    this.outbox = outbox;
  }

  @GetMapping("/applications")
  List<ApplicationLifecycle.Application> applications(@AuthenticationPrincipal Principal p) {
    return applications.list(p.id());
  }

  public record CreateApplication(UUID jobId) {}

  @PostMapping("/applications")
  ApplicationLifecycle.Application create(
      @AuthenticationPrincipal Principal p, @RequestBody CreateApplication input) {
    return applications.create(p.id(), input.jobId());
  }

  public record Transition(ApplicationLifecycle.State state, String note) {}

  @PatchMapping("/applications/{id}")
  ApplicationLifecycle.Application update(
      @AuthenticationPrincipal Principal p, @PathVariable UUID id, @RequestBody Transition input) {
    return applications.update(p.id(), id, input.state(), input.note());
  }

  @GetMapping("/applications/{id}/events")
  List<ApplicationLifecycle.Event> events(
      @AuthenticationPrincipal Principal p, @PathVariable UUID id) {
    return applications.events(p.id(), id);
  }

  public record Generate(
      UUID jobId,
      Outreach.Channel channel,
      UUID recipientId,
      UUID resumeVersionId,
      String instructions,
      String length) {}

  @PostMapping("/outreach/generate")
  AsyncJob generate(
      @AuthenticationPrincipal Principal p,
      @RequestBody Generate input,
      @RequestHeader("Idempotency-Key") String key) {
    return generation.requestOutreach(
        p.id(),
        input.jobId(),
        input.channel(),
        input.recipientId(),
        input.resumeVersionId(),
        input.instructions(),
        input.length(),
        key);
  }

  @GetMapping("/outreach")
  List<Outreach.Message> outreach(@AuthenticationPrincipal Principal p) {
    return approvals.list(p.id());
  }

  @GetMapping("/outreach/{id}")
  ApprovalService.Preview preview(@AuthenticationPrincipal Principal p, @PathVariable UUID id) {
    return approvals.preview(p.id(), id);
  }

  @GetMapping("/outreach/{id}/versions")
  List<Outreach.Version> versions(@AuthenticationPrincipal Principal p, @PathVariable UUID id) {
    return approvals.versions(p.id(), id);
  }

  public record Edit(UUID recipientId, String subject, String body, UUID resumeVersionId) {}

  @PutMapping("/outreach/{id}")
  ApprovalService.Preview revise(
      @AuthenticationPrincipal Principal p, @PathVariable UUID id, @RequestBody Edit input) {
    return approvals.revise(
        p.id(), id, input.recipientId(), input.subject(), input.body(), input.resumeVersionId());
  }

  @PostMapping("/outreach/{id}/request-approval")
  ApprovalService.Preview request(@AuthenticationPrincipal Principal p, @PathVariable UUID id) {
    return approvals.requestApproval(p.id(), id);
  }

  public record ApprovalInput(
      UUID expectedVersionId, String expectedFingerprint, boolean explicitlyApproved) {}

  @PostMapping("/outreach/{id}/approve")
  Outreach.Approval approve(
      @AuthenticationPrincipal Principal p,
      @PathVariable UUID id,
      @RequestBody ApprovalInput input) {
    return approvals.approve(
        p.id(),
        id,
        input.expectedVersionId(),
        input.expectedFingerprint(),
        input.explicitlyApproved());
  }

  @PostMapping("/outreach/{id}/reject")
  void reject(@AuthenticationPrincipal Principal p, @PathVariable UUID id) {
    approvals.reject(p.id(), id);
  }

  @PostMapping("/approvals/{id}/queue")
  AsyncJob queue(@AuthenticationPrincipal Principal p, @PathVariable UUID id) {
    return approvals.queue(p.id(), id);
  }

  @GetMapping("/tasks")
  List<AsyncJob> tasks(@AuthenticationPrincipal Principal p) {
    return outbox.list(p.id());
  }

  @GetMapping("/tasks/{id}")
  AsyncJob task(@AuthenticationPrincipal Principal p, @PathVariable UUID id) {
    return outbox.find(p.id(), id).orElseThrow(DomainException::missing);
  }

  @GetMapping("/connectors")
  List<Ports.ConnectorConfig> connectors(@AuthenticationPrincipal Principal p) {
    return discovery.list(p.id());
  }

  public record ConnectorInput(
      String connector, String board, String role, int intervalMinutes, boolean enabled) {}

  @PostMapping("/connectors")
  Ports.ConnectorConfig connector(
      @AuthenticationPrincipal Principal p, @RequestBody ConnectorInput input) {
    return discovery.configure(
        p.id(),
        input.connector(),
        input.board(),
        input.role(),
        input.intervalMinutes(),
        input.enabled());
  }

  @PostMapping("/connectors/{id}/run")
  AsyncJob run(
      @AuthenticationPrincipal Principal p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key) {
    return discovery.runNow(p.id(), id, key);
  }
}
