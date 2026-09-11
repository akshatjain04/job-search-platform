package io.myjobai.mcp;

import static org.assertj.core.api.Assertions.*;

import io.myjobai.application.*;
import io.myjobai.domain.*;
import io.myjobai.runtime.IdentityService;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class McpAcceptanceTest {
  @Container
  static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

  static ConfigurableApplicationContext context;
  static String endpoint;
  static JsonMapper json;
  static String token;
  static UUID user, job, base, version;
  static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @BeforeAll
  static void start() throws Exception {
    context =
        new SpringApplication(McpApplication.class)
            .run(
                "--APP_MODE=test",
                "--APP_ROLE=mcp",
                "--APP_PUBLIC_URL=http://localhost:8080",
                "--PORT=0",
                "--SUPABASE_DB_URL=" + DATABASE.getJdbcUrl(),
                "--SUPABASE_DB_USER=" + DATABASE.getUsername(),
                "--SUPABASE_DB_PASSWORD=" + DATABASE.getPassword(),
                "--ENCRYPTION_MASTER_KEY=" + Base64.getEncoder().encodeToString(new byte[32]),
                "--LOCAL_STORAGE_PATH=" + Files.createTempDirectory("myjobai-mcp-test-"));
    endpoint =
        "http://localhost:" + context.getEnvironment().getProperty("local.server.port") + "/mcp";
    json = context.getBean(JsonMapper.class);
    var identity = context.getBean(IdentityService.class);
    user = identity.testLogin("mcp@example.com").principal().id();
    token = identity.issueAccess(user, "mcp");
    var profile =
        new Candidate.Profile(
            user,
            new Candidate.Identity("Alex", "alex@example.com", "", "Remote", List.of()),
            List.of(),
            List.of(),
            List.of(),
            List.of("Java"),
            List.of(),
            List.of(),
            Candidate.Preferences.defaults());
    context.getBean(ProfileService.class).save(user, profile);
    var fact =
        context
            .getBean(ProfileService.class)
            .addFact(
                user,
                new Candidate.Fact(
                    UUID.randomUUID(),
                    user,
                    "Acme",
                    "Engineer",
                    "Built Java services",
                    List.of("Java"),
                    Map.of(),
                    null,
                    null,
                    "Verified work history",
                    true));
    job =
        context
            .getBean(JobService.class)
            .ingest(
                user,
                new Ports.DiscoveredJob(
                    "one",
                    "Java Engineer",
                    "Acme",
                    "Remote",
                    "Build Java services",
                    Opportunity.Kind.REFERRAL_POST,
                    true,
                    "FULL_TIME",
                    null,
                    null,
                    "",
                    null,
                    List.of("Java"),
                    Instant.now(),
                    "https://jobs.example/one"),
                "test")
            .id();
    context.getBean(JobService.class).analyze(user, job);
    var rendered =
        context
            .getBean(Ports.ResumeRenderer.class)
            .render(
                new Resume.Structured(
                    profile.identity(),
                    List.of(
                        new Resume.Section(
                            "Acme — Engineer",
                            List.of(new Resume.Bullet(fact.id(), fact.statement())))),
                    fact.skills()),
                "classic");
    var resumes = context.getBean(ResumeService.class);
    base = resumes.upload(user, "base.pdf", "application/pdf", rendered.pdf()).id();
    version =
        context
            .getBean(GenerationService.class)
            .tailor(resumes.tailor(user, base, job, "base"))
            .id();
  }

  @AfterAll
  static void stop() {
    if (context != null) context.close();
  }

  static HttpResponse<String> request(String bearer, Map<String, Object> payload) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json");
    if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
    return HTTP.send(
        builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  static JsonNode rpc(String method, Map<String, Object> params) throws Exception {
    var response =
        request(token, Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params));
    assertThat(response.statusCode()).isEqualTo(200);
    return json.readTree(response.body());
  }

  static JsonNode tool(String name, Map<String, Object> args) throws Exception {
    var response = rpc("tools/call", Map.of("name", name, "arguments", args));
    assertThat(response.has("error")).as(response.toString()).isFalse();
    assertThat(response.path("result").path("isError").asBoolean())
        .as(response.toString())
        .isFalse();
    return response.path("result").path("structuredContent").path("result");
  }

  @Test
  void everyExposedToolUsesRealApplicationServices() throws Exception {
    assertThat(rpc("initialize", Map.of()).path("result").path("protocolVersion").asText())
        .isEqualTo("2025-06-18");
    var listed = rpc("tools/list", Map.of()).path("result").path("tools");
    assertThat(listed.size()).isEqualTo(13);
    assertThat(listed.toString()).doesNotContain("send_arbitrary", "approvals.approve");
    assertThat(tool("jobs.search", Map.of()).path("total").asInt()).isEqualTo(1);
    assertThat(tool("jobs.get", Map.of("jobId", job)).path("id").asText())
        .isEqualTo(job.toString());
    tool("jobs.analyze", Map.of("jobId", job));
    tool("resume.tailor", Map.of("resumeId", base, "jobId", job, "idempotencyKey", "mcp-tailor"));
    assertThat(tool("resume.evaluate", Map.of("versionId", version)).isArray()).isTrue();
    assertThat(tool("resume.render", Map.of("versionId", version)).path("pdf").asText())
        .endsWith("/pdf");
    tool("recruiter.find", Map.of("jobId", job, "idempotencyKey", "research"));
    for (String channel : List.of("email", "linkedin", "whatsapp"))
      tool("outreach.generate_" + channel, Map.of("jobId", job, "idempotencyKey", channel));
    String application = tool("applications.create", Map.of("jobId", job)).path("id").asText();
    tool("applications.update", Map.of("applicationId", application, "state", "SAVED"));
    assertThat(tool("applications.list", Map.of()).size()).isEqualTo(1);
  }

  @Test
  void unauthenticatedForeignAndInvalidRequestsAreRejected() throws Exception {
    assertThat(
            request(null, Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list")).statusCode())
        .isEqualTo(401);
    var bad =
        rpc("tools/call", Map.of("name", "jobs.get", "arguments", Map.of("jobId", "not-a-uuid")));
    assertThat(bad.path("error").path("code").asInt()).isEqualTo(-32602);
    var unknown = rpc("tools/call", Map.of("name", "send_arbitrary_email", "arguments", Map.of()));
    assertThat(unknown.has("error")).isTrue();
    var identity = context.getBean(IdentityService.class);
    String foreign =
        identity.issueAccess(identity.testLogin("foreign@example.com").principal().id(), "mcp");
    var response =
        request(
            foreign,
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                1,
                "method",
                "tools/call",
                "params",
                Map.of("name", "jobs.get", "arguments", Map.of("jobId", job))));
    assertThat(json.readTree(response.body()).path("result").path("isError").asBoolean()).isTrue();
    for (String tool : List.of("jobs.get", "jobs.analyze", "resume.evaluate", "resume.render")) {
      var args =
          Map.<String, Object>of(
              tool.startsWith("resume") ? "versionId" : "jobId", UUID.randomUUID());
      assertThat(
              rpc("tools/call", Map.of("name", tool, "arguments", args))
                  .path("result")
                  .path("isError")
                  .asBoolean())
          .isTrue();
    }
  }
}
