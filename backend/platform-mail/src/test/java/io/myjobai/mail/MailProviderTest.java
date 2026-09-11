package io.myjobai.mail;

import static org.assertj.core.api.Assertions.*;

import io.myjobai.application.*;
import io.myjobai.domain.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MailProviderTest {
  record Reply(int statusCode, String body, HttpRequest request) implements HttpResponse<String> {
    public Optional<HttpResponse<String>> previousResponse() {
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

  static String body(HttpRequest request) throws Exception {
    var bytes = new ByteArrayOutputStream();
    var done = new CompletableFuture<String>();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new Flow.Subscriber<ByteBuffer>() {
              public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
              }

              public void onNext(ByteBuffer b) {
                byte[] part = new byte[b.remaining()];
                b.get(part);
                bytes.writeBytes(part);
              }

              public void onError(Throwable t) {
                done.completeExceptionally(t);
              }

              public void onComplete() {
                done.complete(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
              }
            });
    return done.get(5, TimeUnit.SECONDS);
  }

  static Ports.ApprovedMail mail() {
    UUID user = UUID.randomUUID(),
        message = UUID.randomUUID(),
        recipient = UUID.randomUUID(),
        resume = UUID.randomUUID();
    var version =
        new Outreach.Version(
            UUID.randomUUID(),
            user,
            message,
            recipient,
            "public@example.com",
            "Application — Engineer",
            "My verified experience.",
            resume,
            Normalization.hash("PDF"),
            Instant.EPOCH);
    var approval =
        new Outreach.Approval(
            UUID.randomUUID(),
            user,
            message,
            version.id(),
            resume,
            recipient,
            version.fingerprint(),
            Instant.EPOCH,
            null);
    return new Ports.ApprovedMail(
        approval, version, "PDF".getBytes(), "test-only-token", "candidate@example.com");
  }

  static void assertMime(byte[] bytes, Ports.ApprovedMail mail) throws Exception {
    var mime =
        new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes));
    assertThat(mime.getSubject()).isEqualTo(mail.version().subject());
    assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("public@example.com");
    assertThat(mime.getMessageID()).contains(mail.approval().id().toString());
    var content = (Multipart) mime.getContent();
    assertThat(content.getBodyPart(0).getContent().toString()).contains("My verified experience.");
    assertThat(content.getBodyPart(1).getInputStream().readAllBytes()).isEqualTo(mail.attachment());
  }

  @Test
  void gmailEncodesExactMimeAndReadsProviderReceipt() {
    var mail = mail();
    var provider =
        new GmailMailboxProvider(
            new MailTransport(
                request -> {
                  assertThat(request.uri().toString())
                      .isEqualTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
                  assertThat(request.headers().firstValue("Authorization"))
                      .contains("Bearer test-only-token");
                  assertThat(request.timeout()).contains(java.time.Duration.ofSeconds(40));
                  var raw =
                      JsonMapper.builder().build().readTree(body(request)).path("raw").asText();
                  assertMime(Base64.getUrlDecoder().decode(raw), mail);
                  return new Reply(200, "{\"id\":\"gmail-receipt\"}", request);
                }),
            JsonMapper.builder().build());
    assertThat(provider.send(mail).providerId()).isEqualTo("gmail-receipt");
  }

  @Test
  void outlookEncodesMimeAndRecordsAcceptance() {
    var mail = mail();
    var provider =
        new OutlookMailboxProvider(
            new MailTransport(
                request -> {
                  assertThat(request.uri().toString())
                      .isEqualTo("https://graph.microsoft.com/v1.0/me/sendMail");
                  assertThat(request.headers().firstValue("Content-Type")).contains("text/plain");
                  assertMime(Base64.getDecoder().decode(body(request)), mail);
                  return new Reply(202, "", request);
                }));
    assertThat(provider.send(mail).providerId()).contains(mail.approval().id().toString());
  }

  @Test
  void noAutomaticResendAndFailuresDistinguishAmbiguity() {
    for (int code : List.of(401, 403, 429, 500)) {
      var count = new AtomicInteger();
      var transport =
          new MailTransport(
              request -> {
                count.incrementAndGet();
                return new Reply(code, "untrusted provider error", request);
              });
      assertThatThrownBy(
              () ->
                  transport.post(
                      URI.create("https://mail.example/send"), "secret", "text/plain", "message"))
          .isInstanceOfSatisfying(
              IntegrationException.class,
              e -> {
                assertThat(e.retryable()).isEqualTo(code == 429);
                assertThat(e.ambiguous()).isEqualTo(code >= 500);
                assertThat(e.getMessage()).doesNotContain("untrusted", "secret");
              });
      assertThat(count).hasValue(1);
    }
    var transport =
        new MailTransport(
            request -> {
              throw new IOException("secret network detail");
            });
    assertThatThrownBy(
            () ->
                transport.post(
                    URI.create("https://mail.example/send"), "secret", "text/plain", "message"))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.ambiguous()).isTrue());
  }

  @Test
  void cancellationPreservesInterruptionAndRequiresReconciliation() {
    var transport =
        new MailTransport(
            request -> {
              throw new InterruptedException();
            });
    try {
      assertThatThrownBy(
              () ->
                  transport.post(
                      URI.create("https://mail.example/send"), "secret", "text/plain", "message"))
          .isInstanceOfSatisfying(
              IntegrationException.class,
              e -> {
                assertThat(e.ambiguous()).isTrue();
                assertThat(e.retryable()).isFalse();
              });
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
