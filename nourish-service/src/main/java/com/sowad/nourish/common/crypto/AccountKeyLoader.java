package com.sowad.nourish.common.crypto;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class AccountKeyLoader {
  private AccountKeyLoader() {}
  public static String load(String value, String file) {
    boolean hasValue=value!=null && !value.isBlank(), hasFile=file!=null && !file.isBlank();
    if (hasValue == hasFile) throw new IllegalStateException("Configure exactly one of ACCOUNT_DATA_KEY or ACCOUNT_DATA_KEY_FILE.");
    if (hasValue) return value;
    try {
      Path path=Path.of(file);
      if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) throw new IllegalStateException();
      if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
        Set<PosixFilePermission> mode=Files.getPosixFilePermissions(path);
        if (mode.stream().anyMatch(p->p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) throw new IllegalStateException();
      }
      return Files.readString(path).strip();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read ACCOUNT_DATA_KEY_FILE. Use a regular owner-only key file (chmod 600).");
    }
  }
}
