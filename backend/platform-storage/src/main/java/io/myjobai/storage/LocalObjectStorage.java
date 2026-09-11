package io.myjobai.storage;

import io.myjobai.application.Ports;
import java.nio.file.*;
import java.util.*;

public final class LocalObjectStorage implements Ports.ObjectStorage {
  private final Path root;

  public LocalObjectStorage(Path root) {
    try {
      Files.createDirectories(root);
      this.root = root.toRealPath();
    } catch (Exception e) {
      throw new IllegalArgumentException("Local test storage path is unavailable", e);
    }
  }

  public String put(UUID user, String key, byte[] bytes, String type) {
    String owned = StorageKeys.create(user, key);
    Path path = root.resolve(owned).normalize();
    try {
      Files.createDirectories(path.getParent());
      if (!path.getParent().toRealPath().startsWith(root))
        throw new IllegalArgumentException("Storage path escaped root");
      try {
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
      } catch (FileAlreadyExistsException exists) {
        if (!Arrays.equals(get(user, owned), bytes))
          throw new IllegalStateException("Immutable object already exists with different content");
      }
      return owned;
    } catch (Exception e) {
      throw new IllegalStateException("Object could not be stored", e);
    }
  }

  public byte[] get(UUID user, String key) {
    try {
      Path path = root.resolve(StorageKeys.owned(user, key)).toRealPath();
      if (!path.startsWith(root)) throw new IllegalArgumentException("Storage path escaped root");
      return Files.readAllBytes(path);
    } catch (io.myjobai.domain.DomainException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Object could not be read", e);
    }
  }

  public void delete(UUID user, String key) {
    try {
      Path path = root.resolve(StorageKeys.owned(user, key)).toRealPath();
      if (!path.startsWith(root)) throw new IllegalArgumentException("Storage path escaped root");
      Files.delete(path);
    } catch (Exception e) {
      throw new IllegalStateException("Object could not be deleted", e);
    }
  }
}
