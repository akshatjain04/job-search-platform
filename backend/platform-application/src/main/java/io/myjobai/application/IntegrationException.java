package io.myjobai.application;

public final class IntegrationException extends RuntimeException {
  private final String code;
  private final boolean retryable;
  private final boolean ambiguous;

  public IntegrationException(
      String code, String safeMessage, boolean retryable, boolean ambiguous) {
    super(safeMessage);
    this.code = code;
    this.retryable = retryable;
    this.ambiguous = ambiguous;
  }

  public String code() {
    return code;
  }

  public boolean retryable() {
    return retryable;
  }

  public boolean ambiguous() {
    return ambiguous;
  }
}
