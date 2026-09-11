package io.myjobai.domain;

import java.net.URI;
import java.util.*;

public final class Checks {
  private Checks() {}

  public static String text(String value, String field, int max) {
    if (value == null || value.isBlank() || value.length() > max)
      throw DomainException.invalid(field + " must contain 1 to " + max + " characters");
    return value.strip();
  }

  public static String optional(String value, int max) {
    if (value == null) return "";
    if (value.length() > max) throw DomainException.invalid("Field exceeds " + max + " characters");
    return value.strip();
  }

  public static <T> T required(T value) {
    if (value == null) throw DomainException.invalid("Required field is missing");
    return value;
  }

  public static <T> List<T> list(List<T> values) {
    if (values == null) return List.of();
    if (values.size() > 500 || values.stream().anyMatch(Objects::isNull))
      throw DomainException.invalid("List exceeds 500 items or contains null values");
    return List.copyOf(values);
  }

  public static String email(String value) {
    var email = text(value, "Email", 254).toLowerCase(Locale.ROOT);
    if (!email.matches(
        "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}"))
      throw DomainException.invalid("Invalid email address");
    return email;
  }

  public static String publicUrl(String value) {
    try {
      URI uri = URI.create(text(value, "URL", 2048));
      if (!Set.of("https", "http").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80))
        throw DomainException.invalid(
            "Expected an HTTP(S) URL without credentials or custom ports");
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      if (host.equals("localhost")
          || host.endsWith(".localhost")
          || host.endsWith(".local")
          || !host.contains(".")
          || host.matches("[0-9.]+")
          || host.contains(":")) throw DomainException.invalid("Expected a public hostname");
      return uri.toASCIIString();
    } catch (IllegalArgumentException e) {
      throw DomainException.invalid("Invalid URL");
    }
  }
}
