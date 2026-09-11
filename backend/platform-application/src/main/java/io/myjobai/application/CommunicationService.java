package io.myjobai.application;

import io.myjobai.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

public class CommunicationService {
  public record Reservation(
      Outreach.Approval approval,
      Outreach.Version version,
      Resume.Version resume,
      boolean alreadySent,
      boolean uncertain) {}

  private final Ports.Messages messages;
  private final Ports.Contacts contacts;
  private final Ports.Resumes resumes;
  private final Ports.Applications applications;
  private final Ports.Audit audit;
  private final Clock clock;

  public CommunicationService(
      Ports.Messages messages,
      Ports.Contacts contacts,
      Ports.Resumes resumes,
      Ports.Applications applications,
      Ports.Audit audit,
      Clock clock) {
    this.messages = messages;
    this.contacts = contacts;
    this.resumes = resumes;
    this.applications = applications;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Reservation reserve(UUID user, UUID approvalId) {
    var a = messages.approval(user, approvalId).orElseThrow(DomainException::missing);
    var m = messages.find(user, a.messageId(), true).orElseThrow(DomainException::missing);
    if (m.state() == Outreach.State.SENT) return new Reservation(a, null, null, true, false);
    if (m.state() == Outreach.State.SENDING || m.state() == Outreach.State.DELIVERY_UNKNOWN) {
      messages.state(user, m.id(), Outreach.State.DELIVERY_UNKNOWN, clock.instant());
      audit.record(user, "EMAIL_RECONCILIATION_REQUIRED", "outreach", m.id(), Map.of());
      return new Reservation(a, null, null, false, true);
    }
    if (m.state() != Outreach.State.QUEUED)
      throw DomainException.conflict("Email is not queued with an active approval");
    var v = messages.version(user, a.emailVersionId()).orElseThrow(DomainException::missing);
    var c = contacts.find(user, a.recipientId()).orElseThrow(DomainException::missing);
    var r = resumes.version(user, a.resumeVersionId()).orElseThrow(DomainException::missing);
    a.requireValid(m, v, c, r);
    messages.state(user, m.id(), Outreach.State.SENDING, clock.instant());
    audit.record(
        user, "EMAIL_DISPATCH_STARTED", "outreach", m.id(), Map.of("approval", a.id().toString()));
    return new Reservation(a, v, r, false, false);
  }

  @Transactional
  public void sent(Reservation reservation, Ports.SendResult result) {
    var a = reservation.approval();
    var m = messages.find(a.userId(), a.messageId(), true).orElseThrow(DomainException::missing);
    if (m.state() != Outreach.State.SENDING)
      throw DomainException.conflict("Email dispatch state changed");
    messages.state(a.userId(), m.id(), Outreach.State.SENT, clock.instant());
    audit.record(
        a.userId(),
        result.testMode() ? "EMAIL_TEST_SENT" : "EMAIL_SENT",
        "outreach",
        m.id(),
        Map.of("providerId", result.providerId()));
    var app = applications.forJob(a.userId(), m.jobId());
    if (app.isPresent() && app.get().state() == ApplicationLifecycle.State.OUTREACH_PREPARED) {
      var locked = applications.find(a.userId(), app.get().id(), true).orElseThrow();
      if (locked.state() == ApplicationLifecycle.State.OUTREACH_PREPARED)
        applications.transition(
            locked,
            ApplicationLifecycle.State.OUTREACH_SENT,
            "Approved email sent",
            clock.instant());
    }
  }

  @Transactional
  public void failed(Reservation reservation, IntegrationException failure) {
    var a = reservation.approval();
    messages.find(a.userId(), a.messageId(), true).orElseThrow(DomainException::missing);
    var state =
        failure.ambiguous()
            ? Outreach.State.DELIVERY_UNKNOWN
            : failure.retryable() ? Outreach.State.QUEUED : Outreach.State.FAILED;
    messages.state(a.userId(), a.messageId(), state, clock.instant());
    audit.record(
        a.userId(),
        "EMAIL_SEND_FAILED",
        "outreach",
        a.messageId(),
        Map.of("code", failure.code(), "state", state.name()));
  }

  /**
   * A human reconciles an ambiguous provider outcome; this operation never queues or sends mail.
   */
  @Transactional
  public void reconcile(
      UUID user, UUID messageId, boolean delivered, String evidence, boolean explicitlyConfirmed) {
    if (!explicitlyConfirmed)
      throw DomainException.invalid("Explicit sent-mail reconciliation confirmation is required");
    String note = Checks.text(evidence, "Mailbox evidence", 1000);
    var message = messages.find(user, messageId, true).orElseThrow(DomainException::missing);
    if (message.state() != Outreach.State.DELIVERY_UNKNOWN)
      throw DomainException.conflict("Only uncertain delivery can be reconciled");
    if (!delivered) messages.invalidate(user, messageId, clock.instant());
    messages.state(
        user, messageId, delivered ? Outreach.State.SENT : Outreach.State.FAILED, clock.instant());
    audit.record(
        user,
        "EMAIL_RECONCILED",
        "outreach",
        messageId,
        Map.of("delivered", Boolean.toString(delivered), "evidence", note));
    if (delivered) {
      var app = applications.forJob(user, message.jobId());
      if (app.isPresent()) {
        var locked = applications.find(user, app.get().id(), true).orElseThrow();
        if (locked.state() == ApplicationLifecycle.State.OUTREACH_PREPARED)
          applications.transition(
              locked,
              ApplicationLifecycle.State.OUTREACH_SENT,
              "User confirmed delivery in mailbox",
              clock.instant());
      }
    }
  }
}
