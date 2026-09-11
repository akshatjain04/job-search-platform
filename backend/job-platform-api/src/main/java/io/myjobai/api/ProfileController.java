package io.myjobai.api;

import io.myjobai.application.ProfileService;
import io.myjobai.domain.Candidate;
import io.myjobai.runtime.IdentityService.Principal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
  private final ProfileService profiles;

  public ProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping
  Candidate.Profile get(@AuthenticationPrincipal Principal p) {
    return profiles.get(p.id());
  }

  @PutMapping
  Candidate.Profile save(
      @AuthenticationPrincipal Principal p, @RequestBody Candidate.Profile input) {
    return profiles.save(p.id(), input);
  }

  @GetMapping("/facts")
  List<Candidate.Fact> facts(@AuthenticationPrincipal Principal p) {
    return profiles.facts(p.id());
  }

  @PostMapping("/facts")
  Candidate.Fact fact(@AuthenticationPrincipal Principal p, @RequestBody Candidate.Fact input) {
    return profiles.addFact(p.id(), input);
  }
}
