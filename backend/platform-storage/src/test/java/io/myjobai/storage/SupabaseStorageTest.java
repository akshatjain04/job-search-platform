package io.myjobai.storage;

import static org.assertj.core.api.Assertions.*;

import io.myjobai.application.IntegrationException;
import io.myjobai.domain.DomainException;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class SupabaseStorageTest {
  record Reply(int statusCode, byte[] body, HttpRequest request) implements HttpResponse<byte[]> {
    public Optional<HttpResponse<byte[]>> previousResponse() {
      return Optional.empty();
    }

    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (a, b) -> true);
    }

    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    public URI uri() {
      return request.uri();
    }

    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  @Test
  void immutableUploadRetryChecksBytesAndRejectsDifferentContent() {
    UUID user = UUID.randomUUID();
    var methods = new ArrayList<String>();
    var storage =
        new SupabaseObjectStorage(
            "https://project.supabase.co",
            "resumes",
            "test-only-key",
            request -> {
              methods.add(request.method());
              assertThat(request.uri().getPath())
                  .isEqualTo("/storage/v1/object/resumes/" + user + "/resume.pdf");
              assertThat(request.headers().firstValue("x-upsert")).contains("false");
              return new Reply(
                  request.method().equals("POST") ? 400 : 200,
                  request.method().equals("POST")
                      ? "Asset Already Exists".getBytes()
                      : "PDF".getBytes(),
                  request);
            });
    assertThat(storage.put(user, "resume.pdf", "PDF".getBytes(), "application/pdf"))
        .isEqualTo(user + "/resume.pdf");
    assertThat(methods).containsExactly("POST", "GET");
    assertThatThrownBy(
            () -> storage.put(user, "resume.pdf", "changed".getBytes(), "application/pdf"))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.code()).isEqualTo("STORAGE_CONFLICT"));
    assertThatThrownBy(() -> storage.get(UUID.randomUUID(), user + "/resume.pdf"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void authorizationErrorsAreSanitizedAndNotRetried() {
    var storage =
        new SupabaseObjectStorage(
            "https://project.supabase.co",
            "resumes",
            "test-only-key",
            request -> new Reply(403, "sensitive provider body".getBytes(), request));
    assertThatThrownBy(
            () -> storage.put(UUID.randomUUID(), "resume.pdf", new byte[1], "application/pdf"))
        .isInstanceOfSatisfying(
            IntegrationException.class,
            e -> {
              assertThat(e.retryable()).isFalse();
              assertThat(e.getMessage()).doesNotContain("sensitive", "test-only-key");
            });
  }
}
