package io.myjobai.api;

import io.myjobai.application.*;
import io.myjobai.runtime.IdentityService.Principal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InsightsController {
  private final InsightsService insights;

  public InsightsController(InsightsService insights) {
    this.insights = insights;
  }

  @GetMapping("/analytics")
  Ports.AnalyticsSummary summary(@AuthenticationPrincipal Principal p) {
    return insights.summary(p.id());
  }

  @GetMapping("/audit-events")
  List<Map<String, Object>> activity(@AuthenticationPrincipal Principal p) {
    return insights.activity(p.id());
  }
}
