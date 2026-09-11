package io.myjobai.application;

import io.myjobai.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

public class DiscoveryService {
  private final Map<String, Ports.JobSourceConnector> connectors;
  private final Ports.Connectors configs;
  private final Ports.Outbox outbox;
  private final JobService jobs;
  private final Clock clock;

  public DiscoveryService(
      List<Ports.JobSourceConnector> connectors,
      Ports.Connectors configs,
      Ports.Outbox outbox,
      JobService jobs,
      Clock clock) {
    var registry = new HashMap<String, Ports.JobSourceConnector>();
    connectors.forEach(c -> registry.put(c.key(), c));
    this.connectors = Map.copyOf(registry);
    this.configs = configs;
    this.outbox = outbox;
    this.jobs = jobs;
    this.clock = clock;
  }

  public List<Ports.ConnectorConfig> list(UUID user) {
    return configs.list(user);
  }

  @Transactional
  public Ports.ConnectorConfig configure(
      UUID user, String connector, String board, String role, int interval, boolean enabled) {
    if (!connectors.containsKey(connector)) throw DomainException.invalid("Unknown connector");
    if (interval < 15 || interval > 10080)
      throw DomainException.invalid("Connector interval must be 15 minutes to 7 days");
    var config =
        new Ports.ConnectorConfig(
            UUID.randomUUID(),
            user,
            connector,
            Checks.text(board, "Board", 2000),
            Checks.optional(role, 200),
            interval,
            enabled,
            clock.instant());
    configs.save(config);
    return configs.list(user).stream()
        .filter(c -> c.connector().equals(connector) && c.board().equals(board))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public AsyncJob runNow(UUID user, UUID id, String key) {
    var config =
        configs.list(user).stream()
            .filter(c -> c.id().equals(id))
            .findFirst()
            .orElseThrow(DomainException::missing);
    return outbox.enqueue(
        user,
        "DISCOVER_JOBS",
        Map.of("configId", config.id().toString()),
        Checks.text(key, "Idempotency-Key", 100),
        clock.instant());
  }

  @Transactional
  public void schedule() {
    for (var config : configs.due(clock.instant())) {
      outbox.enqueue(
          config.userId(),
          "DISCOVER_JOBS",
          Map.of("configId", config.id().toString()),
          config.id() + ":" + config.nextRunAt(),
          clock.instant());
      configs.schedule(config.id(), clock.instant().plusSeconds(config.intervalMinutes() * 60L));
    }
  }

  public void discover(AsyncJob task) {
    var config =
        configs.list(task.userId()).stream()
            .filter(c -> c.id().toString().equals(task.payload().get("configId")))
            .findFirst()
            .orElseThrow(DomainException::missing);
    try {
      var found =
          connectors
              .get(config.connector())
              .discoverJobs(new Ports.DiscoveryCriteria(config.board(), config.role(), 100));
      for (var job : found) jobs.ingest(task.userId(), job, config.connector());
      configs.run(config.id(), task.userId(), "COMPLETED", found.size(), null, clock.instant());
    } catch (RuntimeException e) {
      configs.run(
          config.id(),
          task.userId(),
          "FAILED",
          0,
          e instanceof IntegrationException integration ? integration.code() : "DISCOVERY_FAILED",
          clock.instant());
      throw e;
    }
  }
}
