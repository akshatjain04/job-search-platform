package io.myjobai.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TokenCipherTest {
  @Test
  void authenticatedEncryptionUsesUniqueNoncesAndBindsOwnerContext() {
    var cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    String first = cipher.encrypt("test-refresh-token", "owner:mailbox"),
        second = cipher.encrypt("test-refresh-token", "owner:mailbox");
    assertThat(first).isNotEqualTo(second).doesNotContain("test-refresh-token");
    assertThat(cipher.decrypt(first, "owner:mailbox")).isEqualTo("test-refresh-token");
    assertThatThrownBy(() -> cipher.decrypt(first, "stranger:mailbox"))
        .hasMessageContaining("authenticated");
    String tampered = first.substring(0, first.length() - 4) + "AAAA";
    assertThatThrownBy(() -> cipher.decrypt(tampered, "owner:mailbox"))
        .hasMessageContaining("authenticated");
    assertThatThrownBy(() -> new TokenCipher("invalid")).hasMessageContaining("32 random bytes");
  }

  @Test
  void cacheCanonicalizationIsIndependentOfMapInsertionOrder() {
    var rows = new JsonRows(null, JsonMapper.builder().build());
    var first = new LinkedHashMap<String, Object>();
    first.put("z", Map.of("b", 2, "a", 1));
    first.put("a", List.of("second", "first"));
    var second = new LinkedHashMap<String, Object>();
    second.put("a", List.of("second", "first"));
    second.put("z", Map.of("a", 1, "b", 2));
    assertThat(rows.canonical(first)).isEqualTo(rows.canonical(second));
    assertThat(rows.canonical(first))
        .isNotEqualTo(
            rows.canonical(Map.of("a", List.of("first", "second"), "z", Map.of("a", 1, "b", 2))));
  }
}
