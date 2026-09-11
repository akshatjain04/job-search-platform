package io.myjobai.persistence;

import static io.myjobai.persistence.JsonRows.*;

import io.myjobai.application.Ports;
import java.util.*;

public final class JdbcMailboxes implements Ports.Mailboxes {
  private final JsonRows rows;
  private final TokenCipher cipher;

  public JdbcMailboxes(JsonRows rows, TokenCipher cipher) {
    this.rows = rows;
    this.cipher = cipher;
  }

  public Optional<Ports.Mailbox> find(UUID user) {
    return rows
        .jdbc
        .query(
            "SELECT * FROM app.mailbox_connections WHERE user_id=?",
            (r, n) ->
                new Ports.Mailbox(
                    user,
                    r.getString("provider"),
                    r.getString("address"),
                    cipher.decrypt(r.getString("encrypted_access_token"), user + ":mail-access"),
                    cipher.decrypt(r.getString("encrypted_refresh_token"), user + ":mail-refresh"),
                    instant(r, "expires_at")),
            user)
        .stream()
        .findFirst();
  }

  public void save(Ports.Mailbox box) {
    rows.jdbc.update(
        "INSERT INTO app.mailbox_connections(user_id,provider,address,encrypted_access_token,encrypted_refresh_token,expires_at) VALUES (?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET provider=excluded.provider,address=excluded.address,encrypted_access_token=excluded.encrypted_access_token,encrypted_refresh_token=excluded.encrypted_refresh_token,expires_at=excluded.expires_at",
        box.userId(),
        box.provider(),
        box.address(),
        cipher.encrypt(box.accessToken(), box.userId() + ":mail-access"),
        cipher.encrypt(box.refreshToken(), box.userId() + ":mail-refresh"),
        timestamp(box.expiresAt()));
  }
}
