package io.myjobai.ai;

import io.myjobai.application.IntegrationException;
import java.util.*;

/**
 * Enforces the deliberately small JSON Schema vocabulary used by platform tasks. Unknown keywords
 * fail closed.
 */
public final class JsonSchemaValidator {
  private static final Set<String> VOCABULARY =
      Set.of(
          "type",
          "properties",
          "required",
          "additionalProperties",
          "items",
          "enum",
          "minItems",
          "maxItems",
          "uniqueItems",
          "minLength",
          "maxLength",
          "minimum",
          "maximum",
          "description",
          "format");

  private JsonSchemaValidator() {}

  public static void validate(Map<String, Object> schema, Object value) {
    validate(schema, value, "$");
  }

  @SuppressWarnings("unchecked")
  private static void validate(Map<String, Object> schema, Object value, String path) {
    if (!VOCABULARY.containsAll(schema.keySet()))
      throw invalid("Unsupported schema vocabulary at " + path);
    String type = String.valueOf(schema.get("type"));
    boolean valid =
        switch (type) {
          case "object" -> value instanceof Map;
          case "array" -> value instanceof List;
          case "string" -> value instanceof String;
          case "boolean" -> value instanceof Boolean;
          case "number" -> value instanceof Number n && Double.isFinite(n.doubleValue());
          case "integer" ->
              value instanceof Number n
                  && Double.isFinite(n.doubleValue())
                  && n.doubleValue() == Math.rint(n.doubleValue());
          case "null" -> value == null;
          default -> false;
        };
    if (!valid) throw invalid("Expected " + type + " at " + path);
    if (schema.containsKey("enum") && !((List<?>) schema.get("enum")).contains(value))
      throw invalid("Value outside enum at " + path);
    if (value instanceof Map<?, ?> object) {
      var props = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
      for (Object key : (List<?>) schema.getOrDefault("required", List.of()))
        if (!object.containsKey(key)) throw invalid("Missing field at " + path + "." + key);
      for (var entry : object.entrySet()) {
        if (!props.containsKey(entry.getKey())) {
          if (Boolean.FALSE.equals(schema.get("additionalProperties")))
            throw invalid("Unexpected field at " + path);
        } else
          validate(
              (Map<String, Object>) props.get(entry.getKey()),
              entry.getValue(),
              path + "." + entry.getKey());
      }
    }
    if (value instanceof List<?> list) {
      bound(schema, "minItems", "maxItems", list.size(), path);
      if (Boolean.TRUE.equals(schema.get("uniqueItems"))
          && new HashSet<>(list).size() != list.size())
        throw invalid("Duplicate values at " + path);
      for (Object item : list)
        validate((Map<String, Object>) schema.get("items"), item, path + "[]");
    }
    if (value instanceof String string) {
      bound(schema, "minLength", "maxLength", string.length(), path);
      if ("uuid".equals(schema.get("format")))
        try {
          UUID.fromString(string);
        } catch (IllegalArgumentException e) {
          throw invalid("Expected UUID at " + path);
        }
    }
    if (value instanceof Number number)
      bound(schema, "minimum", "maximum", number.doubleValue(), path);
  }

  private static void bound(
      Map<String, Object> schema, String min, String max, double value, String path) {
    if (schema.get(min) instanceof Number lower && value < lower.doubleValue()
        || schema.get(max) instanceof Number upper && value > upper.doubleValue())
      throw invalid("Value outside bounds at " + path);
  }

  private static IntegrationException invalid(String message) {
    return new IntegrationException("AI_SCHEMA", message, false, false);
  }
}
