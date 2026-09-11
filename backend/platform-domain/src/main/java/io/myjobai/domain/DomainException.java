package io.myjobai.domain;

public class DomainException extends RuntimeException {
    private final String code;
    public DomainException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
    public static DomainException invalid(String message) { return new DomainException("VALIDATION", message); }
    public static DomainException missing() { return new DomainException("NOT_FOUND", "Resource not found"); }
    public static DomainException conflict(String message) { return new DomainException("CONFLICT", message); }
}
