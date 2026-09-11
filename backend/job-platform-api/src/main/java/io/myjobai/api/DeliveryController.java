package io.myjobai.api;

import io.myjobai.application.CommunicationService;
import io.myjobai.runtime.IdentityService.Principal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/outreach")
public class DeliveryController {
  private final CommunicationService communication;

  public DeliveryController(CommunicationService communication) {
    this.communication = communication;
  }

  public record Reconcile(boolean delivered, String evidence, boolean explicitlyConfirmed) {}

  @PostMapping("/{id}/reconcile")
  void reconcile(
      @AuthenticationPrincipal Principal user,
      @PathVariable UUID id,
      @RequestBody Reconcile input) {
    communication.reconcile(
        user.id(), id, input.delivered(), input.evidence(), input.explicitlyConfirmed());
  }
}
