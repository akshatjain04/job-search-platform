package io.myjobai.storage;

import io.myjobai.application.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class SupabaseObjectStorage implements Ports.ObjectStorage {
  @FunctionalInterface
  interface Exchange {
    HttpResponse<byte[]> send(HttpRequest request) throws Exception;
  }

  private final URI base;
  private final String bucket, key;
  private final Exchange exchange;

  public SupabaseObjectStorage(String url, String bucket, String key) {
    this(url, bucket, key, defaultExchange());
  }

  private static Exchange defaultExchange() {
    var http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    return request -> http.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  SupabaseObjectStorage(String url, String bucket, String key, Exchange exchange) {
    this.base = URI.create(url);
    if (!"https".equals(base.getScheme())
        || base.getHost() == null
        || base.getUserInfo() != null
        || !bucket.matches("[A-Za-z0-9_-]+")
        || key.isBlank())
      throw new IllegalArgumentException(
          "Supabase storage requires HTTPS SUPABASE_URL, STORAGE_BUCKET and SUPABASE_SERVICE_ROLE_KEY");
    this.bucket = bucket;
    this.key = key;
    this.exchange = Objects.requireNonNull(exchange);
  }

  private HttpResponse<byte[]> request(String method, String owned, byte[] bytes, String type) {
    var builder =
        HttpRequest.newBuilder(base.resolve("/storage/v1/object/" + bucket + "/" + owned))
            .timeout(Duration.ofSeconds(30))
            .header("apikey", key)
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", type)
            .header("x-upsert", "false");
    builder.method(
        method,
        bytes == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(bytes));
    try {
      return exchange.send(builder.build());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IntegrationException(
          "STORAGE_CANCELLED", "Storage request cancelled", false, false);
    } catch (Exception e) {
      throw new IntegrationException(
          "STORAGE_CONNECTION", "Supabase Storage connection failed", true, false);
    }
  }

  public String put(UUID user, String path, byte[] bytes, String type) {
    String owned = StorageKeys.create(user, path);
    var response = request("POST", owned, bytes, type);
    if (response.statusCode() == 409
        || (response.statusCode() == 400
            && new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT)
                .contains("already exists"))) {
      if (Arrays.equals(get(user, owned), bytes)) return owned;
      throw new IntegrationException(
          "STORAGE_CONFLICT",
          "Immutable object already exists with different content",
          false,
          false);
    }
    success(response.statusCode());
    return owned;
  }

  public byte[] get(UUID user, String path) {
    var response = request("GET", StorageKeys.owned(user, path), null, "application/octet-stream");
    success(response.statusCode());
    if (response.body().length > 12 * 1024 * 1024)
      throw new IntegrationException(
          "STORAGE_SIZE", "Stored object exceeds size limit", false, false);
    return response.body();
  }

  public void delete(UUID user, String path) {
    var response = request("DELETE", StorageKeys.owned(user, path), null, "application/json");
    success(response.statusCode());
  }

  private static void success(int status) {
    if (status < 200 || status >= 300)
      throw new IntegrationException(
          "STORAGE_HTTP",
          "Supabase Storage returned HTTP " + status,
          status == 429 || status >= 500,
          false);
  }
}
