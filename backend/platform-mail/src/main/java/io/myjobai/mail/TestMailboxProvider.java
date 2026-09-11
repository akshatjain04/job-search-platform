package io.myjobai.mail;

import io.myjobai.application.Ports;

/** Acceptance sink: never opens a network connection. Production wiring explicitly rejects this provider. */
public final class TestMailboxProvider implements Ports.MailboxProvider {
    public String key(){return "test";}
    public Ports.SendResult send(Ports.ApprovedMail mail){MimeMessages.build(mail);return new Ports.SendResult("test:"+mail.approval().id(),true);}
}
