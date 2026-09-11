package io.myjobai.mail;

import io.myjobai.application.IntegrationException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** No automatic resend: a connection failure after a POST may already have delivered mail. */
public final class MailTransport {
  @FunctionalInterface
  interface Exchange {
    HttpResponse<String> send(HttpRequest request) throws Exception;
  }

  private final Exchange exchange;

  public MailTransport() {
    var http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    exchange = request -> http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  MailTransport(Exchange exchange) {
    this.exchange = Objects.requireNonNull(exchange);
  }

  public HttpResponse<String> post(URI endpoint, String token, String contentType, String body) {
    var request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(40))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      var response = exchange.send(request);
      int code = response.statusCode();
      if (code >= 200 && code < 300) return response;
      throw new IntegrationException(
          code == 429
              ? "MAIL_RATE_LIMIT"
              : code == 401 || code == 403 ? "MAIL_AUTH" : "MAIL_PROVIDER",
          "Mailbox provider returned HTTP " + code,
          code == 429,
          code >= 500);
    } catch (IntegrationException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IntegrationException(
          "MAIL_CANCELLED",
          "Delivery may have occurred before cancellation; reconcile with sent mail",
          false,
          true);
    } catch (Exception e) {
      throw new IntegrationException(
          "MAIL_DELIVERY_UNKNOWN",
          "Delivery could not be confirmed; reconcile with sent mail before retrying",
          false,
          true);
    }
  }
}
