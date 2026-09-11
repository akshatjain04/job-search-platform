package io.myjobai.domain;

import java.time.*;
import java.util.*;

public record AsyncJob(
    UUID id,
    UUID userId,
    String eventType,
    Map<String, String> payload,
    Status status,
    int attemptCount,
    int maxAttempts,
    Instant availableAt,
    Instant lockedAt,
    String lockedBy,
    String lastError,
    Instant createdAt,
    Instant completedAt,
    String idempotencyKey) {
  public enum Status {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
  }

  public static Duration retryDelay(int attempt) {
    if (attempt < 1) throw DomainException.invalid("Attempt must be positive");
    return Duration.ofSeconds(Math.min(3600, 5L << Math.min(attempt - 1, 10)));
  }
}
