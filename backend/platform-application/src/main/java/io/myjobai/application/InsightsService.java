package io.myjobai.application;

import java.util.*;

public final class InsightsService {
  private final Ports.Analytics analytics;

  public InsightsService(Ports.Analytics analytics) {
    this.analytics = analytics;
  }

  public Ports.AnalyticsSummary summary(UUID user) {
    return analytics.summary(user);
  }

  public List<Map<String, Object>> activity(UUID user) {
    return analytics.activity(user, 100);
  }
}
