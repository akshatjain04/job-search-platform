package io.myjobai.domain;

import java.time.Instant;
import java.util.*;

public final class Outreach {
    private Outreach() {}
    public enum Channel { EMAIL, LINKEDIN, WHATSAPP }
    public enum State { GENERATING, DRAFT, AWAITING_APPROVAL, REJECTED, APPROVED, QUEUED, SENDING, FAILED, SENT, DELIVERY_UNKNOWN }
    public record Message(UUID id, UUID userId, UUID jobId, Channel channel, State state, UUID currentVersionId, Instant updatedAt) {}
    public record Version(UUID id, UUID userId, UUID messageId, UUID recipientId, String recipientValue, String subject,
                          String body, UUID resumeVersionId, String attachmentHash, Instant createdAt) {
        public Version {
            Objects.requireNonNull(id); Objects.requireNonNull(userId); Objects.requireNonNull(messageId);
            subject=Checks.optional(subject,200); body=Checks.text(body,"Message body",16000); recipientValue=Checks.optional(recipientValue,300); attachmentHash=Checks.optional(attachmentHash,64);
            if (subject.contains("\r") || subject.contains("\n") || recipientValue.contains("\r") || recipientValue.contains("\n")) throw DomainException.invalid("Header line breaks are prohibited");
        }
        public String fingerprint() { return Normalization.hash(String.join("\u0000",id.toString(),userId.toString(),String.valueOf(recipientId),recipientValue,subject,body,String.valueOf(resumeVersionId),attachmentHash)); }
    }
    public record Approval(UUID id, UUID userId, UUID messageId, UUID emailVersionId, UUID resumeVersionId,
                           UUID recipientId, String fingerprint, Instant approvedAt, Instant invalidatedAt) {
        public void requireValid(Message message, Version version, Contact contact, Resume.Version resume) {
            if (invalidatedAt != null || !userId.equals(message.userId()) || !userId.equals(version.userId()) || !userId.equals(contact.userId()) || !userId.equals(resume.userId())
                || !messageId.equals(message.id()) || !messageId.equals(version.messageId()) || !emailVersionId.equals(version.id()) || !emailVersionId.equals(message.currentVersionId())
                || !recipientId.equals(contact.id()) || !recipientId.equals(version.recipientId()) || !contact.value().equals(version.recipientValue())
                || !resumeVersionId.equals(resume.id()) || !resumeVersionId.equals(version.resumeVersionId()) || !resume.pdfHash().equals(version.attachmentHash())
                || !fingerprint.equals(version.fingerprint()) || !contact.sendable() || message.channel()!=Channel.EMAIL)
                throw DomainException.conflict("Approval is invalid, stale, or references an unverified contact");
        }
    }
}
