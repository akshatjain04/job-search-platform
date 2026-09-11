package io.myjobai.mail;

import io.myjobai.application.Ports;
import java.net.URI;
import java.util.Base64;

public final class OutlookMailboxProvider implements Ports.MailboxProvider {
  private final MailTransport transport;

  public OutlookMailboxProvider(MailTransport transport) {
    this.transport = transport;
  }

  public String key() {
    return "outlook";
  }

  public Ports.SendResult send(Ports.ApprovedMail mail) {
    transport.post(
        URI.create("https://graph.microsoft.com/v1.0/me/sendMail"),
        mail.mailboxAccessToken(),
        "text/plain",
        Base64.getEncoder().encodeToString(MimeMessages.build(mail)));
    return new Ports.SendResult("graph-accepted:" + mail.approval().id(), false);
  }
}
