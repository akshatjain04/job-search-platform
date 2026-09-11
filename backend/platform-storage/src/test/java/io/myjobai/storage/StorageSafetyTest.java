package io.myjobai.storage;

import static org.assertj.core.api.Assertions.*;

import io.myjobai.domain.DomainException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageSafetyTest {
  @TempDir Path root;

  @Test
  void immutableOwnerScopedObjectsRejectTraversalAndReplacement() {
    var storage = new LocalObjectStorage(root);
    var user = UUID.randomUUID();
    byte[] bytes = {1, 2, 3};
    String key = storage.put(user, "resumes/base.pdf", bytes, "application/pdf");
    assertThat(storage.get(user, key)).isEqualTo(bytes);
    assertThat(storage.put(user, "resumes/base.pdf", bytes, "application/pdf")).isEqualTo(key);
    assertThatThrownBy(() -> storage.get(UUID.randomUUID(), key))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> storage.put(user, "../escape", bytes, "application/pdf"))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> storage.put(user, "resumes/base.pdf", new byte[] {4}, "application/pdf"))
        .isInstanceOf(RuntimeException.class);
  }
}
