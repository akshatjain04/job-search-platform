package io.myjobai.runtime;

import io.myjobai.application.IntegrationException;
import io.myjobai.domain.DomainException;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(DomainException.class)
  ResponseEntity<Map<String, Object>> domain(DomainException e) {
    return response(
        switch (e.code()) {
          case "NOT_FOUND" -> 404;
          case "CONFLICT" -> 409;
          default -> 400;
        },
        e.code(),
        e.getMessage());
  }

  @ExceptionHandler(IntegrationException.class)
  ResponseEntity<Map<String, Object>> integration(IntegrationException e) {
    return response(e.retryable() ? 503 : 422, e.code(), e.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, Object>> invalid(Exception e) {
    return response(400, "VALIDATION", "Request fields are invalid");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, Object>> integrity(Exception e) {
    return response(
        409, "CONFLICT", "Resource references or concurrent changes conflict with this request");
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingRequestValueException.class,
    org.springframework.web.multipart.MultipartException.class
  })
  ResponseEntity<Map<String, Object>> missing(Exception e) {
    return response(
        400, "VALIDATION", "Required request value missing or upload exceeds supported limits");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> other(Exception e) {
    log.error("Unhandled request failure", safeTrace(e));
    return response(
        500, "INTERNAL", "Request failed; use the request ID when checking server logs");
  }

  public static RuntimeException safeTrace(Exception failure) {
    var safe = new RuntimeException(failure.getClass().getName());
    safe.setStackTrace(failure.getStackTrace());
    return safe;
  }

  private ResponseEntity<Map<String, Object>> response(int status, String code, String message) {
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "code",
                code,
                "message",
                message,
                "requestId",
                Objects.toString(MDC.get("requestId"), "")));
  }
}
