package io.myjobai.domain;

import java.time.Instant;
import java.util.*;

public record Contact(
    UUID id,
    UUID userId,
    UUID recruiterId,
    String name,
    String value,
    Type type,
    String sourceUrl,
    String sourceType,
    double confidence,
    Verification verificationStatus,
    Instant discoveredAt,
    String verificationMethod) {
  public enum Type {
    EMAIL,
    PHONE,
    LINKEDIN
  }

  public enum Verification {
    PUBLIC,
    PROVIDER_VERIFIED,
    USER_VERIFIED,
    UNVERIFIED,
    INFERRED
  }

  public Contact {
    Checks.required(id);
    Checks.required(userId);
    Checks.required(recruiterId);
    Checks.required(type);
    Checks.required(verificationStatus);
    name = Checks.text(name, "Contact name", 200);
    value = Checks.text(value, "Contact value", 300);
    sourceUrl = Checks.publicUrl(sourceUrl);
    sourceType = Checks.text(sourceType, "Source type", 100);
    verificationMethod = Checks.text(verificationMethod, "Verification method", 300);
    Checks.required(discoveredAt);
    if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)
      throw DomainException.invalid("Confidence must be between 0 and 1");
    if (type == Type.EMAIL) value = Checks.email(value);
    if (type == Type.LINKEDIN
        && !java.net
            .URI
            .create(Checks.publicUrl(value))
            .getHost()
            .matches("(?:[a-z]+\\.)?linkedin\\.com"))
      throw DomainException.invalid("Expected an actual LinkedIn URL");
  }

  public boolean sendable() {
    return type == Type.EMAIL
        && Set.of(Verification.PUBLIC, Verification.PROVIDER_VERIFIED, Verification.USER_VERIFIED)
            .contains(verificationStatus);
  }

  public record Recruiter(
      UUID id, UUID userId, String name, String company, String sourceUrl, Instant discoveredAt) {}
}
