package io.myjobai.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mailboxes")
@ConditionalOnProperty(name="APP_ROLE",havingValue="api",matchIfMissing=true)
public class MailboxController {
    private final MailboxOAuth oauth;private final PlatformSettings settings;
    public MailboxController(MailboxOAuth oauth,PlatformSettings settings){this.oauth=oauth;this.settings=settings;}
    @GetMapping Map<String,Object> status(@AuthenticationPrincipal IdentityService.Principal p){return oauth.status(p.id());}
    @GetMapping("/{provider}/connect") ResponseEntity<Void> connect(@AuthenticationPrincipal IdentityService.Principal p,@PathVariable String provider){return ResponseEntity.status(302).location(URI.create(oauth.begin(p.id(),provider))).build();}
    @GetMapping("/{provider}/callback") ResponseEntity<Void> callback(@AuthenticationPrincipal IdentityService.Principal p,@PathVariable String provider,@RequestParam String state,@RequestParam String code){oauth.finish(p.id(),provider,state,code);return ResponseEntity.status(302).location(URI.create(settings.publicUrl()+"/settings")).build();}
    @DeleteMapping void disconnect(@AuthenticationPrincipal IdentityService.Principal p){oauth.disconnect(p.id());}
}
