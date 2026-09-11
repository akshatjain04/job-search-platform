package io.myjobai.persistence;

import io.myjobai.application.*;
import io.myjobai.domain.*;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class JdbcIntelligence implements Ports.Intelligence {
  private final JsonRows rows;
  private final Ports.LlmProvider provider;
  private final String routingFingerprint;
  private final int dailyLimit;
  private final TransactionTemplate accounting;

  private record Outcome(Ports.Completion completion, RuntimeException failure) {}

  public JdbcIntelligence(
      JsonRows rows,
      Ports.LlmProvider provider,
      String routingFingerprint,
      int dailyLimit,
      PlatformTransactionManager transactions) {
    this.rows = rows;
    this.provider = provider;
    this.routingFingerprint = routingFingerprint;
    this.dailyLimit = dailyLimit;
    this.accounting = new TransactionTemplate(transactions);
    accounting.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    if (dailyLimit < 1)
      throw new IllegalArgumentException("LLM_DAILY_REQUEST_LIMIT must be positive");
  }

  public Ports.Completion generate(UUID user, Ports.LlmRequest request) {
    // A billed call remains accounted for even if grounding or rendering later rolls back.
    Outcome result =
        Objects.requireNonNull(
            accounting.execute(
                status -> {
                  try {
                    return new Outcome(account(user, request), null);
                  } catch (IntegrationException e) {
                    if (!e.code().equals("AI_DAILY_LIMIT"))
                      rows.jdbc.update(
                          "INSERT INTO app.ai_usage(id,user_id,task,provider,model,input_tokens,output_tokens,latency_ms,cached,outcome) VALUES (?,?,?,?,?,0,0,0,false,?)",
                          UUID.randomUUID(),
                          user,
                          request.task(),
                          provider.key(),
                          "unreported",
                          e.code());
                    return new Outcome(null, e);
                  }
                }));
    if (result.failure() != null) throw result.failure();
    return result.completion();
  }

  private Ports.Completion account(UUID user, Ports.LlmRequest request) {
    String key = Normalization.hash(routingFingerprint + rows.canonical(request));
    // One per-user lock enforces the spend cap and prevents concurrent cache misses from billing
    // twice.
    rows.jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "ai:" + user);
    var cached =
        rows.jdbc.query(
            "SELECT output,usage FROM app.ai_cache WHERE user_id=? AND cache_key=?",
            (r, n) ->
                new Ports.Completion(
                    rows.json.readValue(
                        r.getString("output"),
                        new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}),
                    rows.read(r.getString("usage"), Ports.Usage.class)),
            user,
            key);
    Ports.Completion completion;
    boolean hit = !cached.isEmpty();
    if (hit) completion = cached.getFirst();
    else {
      long count =
          Objects.requireNonNull(
              rows.jdbc.queryForObject(
                  "SELECT count(*) FROM app.ai_usage WHERE user_id=? AND at>=date_trunc('day',now()) AND NOT cached",
                  Long.class,
                  user));
      if (count >= dailyLimit)
        throw new IntegrationException(
            "AI_DAILY_LIMIT", "Daily AI request budget exhausted", false, false);
      completion = provider.generate(request);
      rows.jdbc.update(
          "INSERT INTO app.ai_cache(user_id,cache_key,output,usage) VALUES (?,?,?::jsonb,?::jsonb)",
          user,
          key,
          rows.write(completion.output()),
          rows.write(completion.usage()));
    }
    var usage = completion.usage();
    rows.jdbc.update(
        "INSERT INTO app.ai_usage(id,user_id,task,provider,model,input_tokens,output_tokens,latency_ms,estimated_cost,cached) VALUES (?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        user,
        request.task(),
        usage.provider(),
        usage.model(),
        hit ? 0 : usage.inputTokens(),
        hit ? 0 : usage.outputTokens(),
        hit ? 0 : usage.latencyMs(),
        hit ? 0 : usage.estimatedCost(),
        hit);
    return completion;
  }
}
