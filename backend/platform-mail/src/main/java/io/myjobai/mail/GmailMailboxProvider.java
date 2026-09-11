package io.myjobai.mail;

import io.myjobai.application.Ports;
import java.net.URI;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class GmailMailboxProvider implements Ports.MailboxProvider {
  private final MailTransport transport;
  private final JsonMapper json;

  public GmailMailboxProvider(MailTransport transport, JsonMapper json) {
    this.transport = transport;
    this.json = json;
  }

  public String key() {
    return "gmail";
  }

  public Ports.SendResult send(Ports.ApprovedMail mail) {
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(MimeMessages.build(mail));
    var result =
        transport.post(
            URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"),
            mail.mailboxAccessToken(),
            "application/json",
            json.writeValueAsString(Map.of("raw", raw)));
    String id = json.readTree(result.body()).path("id").asText();
    if (id.isBlank())
      throw new io.myjobai.application.IntegrationException(
          "MAIL_DELIVERY_UNKNOWN",
          "Gmail accepted mail without a message ID; reconcile sent mail",
          false,
          true);
    return new Ports.SendResult(id, false);
  }
}
