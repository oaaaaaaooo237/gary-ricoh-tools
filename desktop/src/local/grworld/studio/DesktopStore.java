package local.grworld.studio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import local.grworld.studio.spi.TaskStore;

final class DesktopStore implements TaskStore {
  final Path root;

  DesktopStore(Path root) throws IOException {
    this.root = root.toAbsolutePath().normalize();
    Files.createDirectories(this.root);
  }

  Path path(String key) {
    if (!key.matches("[A-Za-z0-9_.-]{1,160}")) throw new IllegalArgumentException("记录名称无效");
    return root.resolve(key);
  }

  public byte[] read(String key) throws IOException {
    Path p = path(key);
    int max = key.equals("image-source.image") ? 40 * 1024 * 1024 : 8 * 1024 * 1024;
    if (Files.size(p) > max) throw new IOException("记录过大");
    return DesktopResources.limited(Files.newInputStream(p), max);
  }

  public boolean exists(String key) {
    return Files.isRegularFile(path(key));
  }

  public synchronized void write(String key, byte[] data) throws IOException {
    Path target = path(key), tmp = path(key + ".tmp");
    try (FileOutputStream out = new FileOutputStream(tmp.toFile())) {
      out.write(data);
      out.getFD().sync();
    }
    try {
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public List<String> list(String prefix) throws IOException {
    try (var files = Files.list(root)) {
      return files
          .map(p -> p.getFileName().toString())
          .filter(n -> n.startsWith(prefix) && !n.endsWith(".tmp"))
          .sorted()
          .toList();
    }
  }

  Properties properties(String key) throws IOException {
    Properties p = new Properties();
    if (exists(key)) p.load(new ByteArrayInputStream(read(key)));
    return p;
  }

  void properties(String key, Properties p) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    p.store(b, null);
    write(key, b.toByteArray());
  }
}
