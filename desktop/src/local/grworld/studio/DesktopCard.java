package local.grworld.studio;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import local.grworld.studio.spi.CardAccess;

final class DesktopCard implements CardAccess, SdCardDeployment.Card {
  final Path root;
  final String identity;
  final boolean writable, fixture;
  final long size;

  DesktopCard(Path path, boolean writable, boolean fixture) throws Exception {
    this(path, writable, fixture, "模拟卡");
  }

  DesktopCard(Path path, boolean writable, boolean fixture, String label) throws Exception {
    root = path.toAbsolutePath().normalize();
    this.writable = writable;
    this.fixture = fixture;
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(root)
        || !root.toRealPath().equals(root)) throw new IOException("请选择实际卡根目录，不使用链接或重定向目录");
    FileStore store = Files.getFileStore(root);
    size = store.getTotalSpace();
    if (fixture) {
      identity = label + "/" + root.getFileName();
    } else {
      if (!root.equals(root.getRoot())
          || root.toString()
              .substring(0, 2)
              .equalsIgnoreCase(System.getenv().getOrDefault("SystemDrive", "C:")))
        throw new IOException("请选择可移除存储卡根目录，不能使用系统盘或普通文件夹");
      if (!Boolean.TRUE.equals(store.getAttribute("volume:isRemovable")))
        throw new IOException("此设备未被 Windows 识别为可移除存储卡；可使用手动 ZIP 与只读读回。");
      identity = String.valueOf(store.getAttribute("volume:vsn")) + "/" + size;
    }
    check();
  }

  static List<Path> volumes() {
    List<Path> roots = new ArrayList<>();
    for (File f : File.listRoots())
      try {
        FileStore s = Files.getFileStore(f.toPath());
        if (Boolean.TRUE.equals(s.getAttribute("volume:isRemovable")) && !s.isReadOnly())
          roots.add(f.toPath());
      } catch (Exception ignored) {
      }
    return roots;
  }

  public String key() {
    return identity;
  }

  public void check() throws Exception {
    if (!Files.isDirectory(root) || !root.toRealPath().equals(root))
      throw new IOException("卡片已断开或位置改变");
    FileStore s = Files.getFileStore(root);
    if (writable && s.isReadOnly()) throw new IOException("卡片已变为只读");
    if (!fixture
        && (!identity.equals(s.getAttribute("volume:vsn") + "/" + s.getTotalSpace())
            || !Boolean.TRUE.equals(s.getAttribute("volume:isRemovable"))))
      throw new IOException("卡片或卷标识已变化");
  }

  Path find(String path, boolean missing) throws Exception {
    if (path.startsWith("/") || path.contains("..") || path.contains("\\") || path.contains(":"))
      throw new IOException("卡片路径无效");
    Path current = root;
    if (path.isEmpty()) return current;
    for (String part : path.split("/")) {
      if (part.isEmpty() || part.equals(".")) throw new IOException("卡片路径无效");
      Path next = current.resolve(part);
      if (Files.isDirectory(current)) {
        try (var children = Files.list(current)) {
          List<Path> matches =
              children.filter(p -> p.getFileName().toString().equalsIgnoreCase(part)).toList();
          if (matches.size() > 1) throw new IOException("卡内存在大小写重名文件");
          if (matches.size() == 1) next = matches.get(0);
        }
      }
      if (Files.exists(next, LinkOption.NOFOLLOW_LINKS)
          && (Files.isSymbolicLink(next)
              || !next.toRealPath().startsWith(root)
              || !Files.getFileStore(next).equals(Files.getFileStore(root))))
        throw new IOException("卡内存在链接或其他卷");
      current = next;
    }
    if (!missing && !Files.exists(current, LinkOption.NOFOLLOW_LINKS))
      throw new FileNotFoundException(path);
    return current;
  }

  void mutable() throws Exception {
    if (!writable) throw new IOException("本次仅供只读核验");
    check();
  }

  public List<String> list(String dir) throws Exception {
    check();
    try (var files = Files.list(find(dir, false))) {
      List<String> result = files.map(p -> p.getFileName().toString()).toList();
      if (result.size() > 4096) throw new IOException("卡目录文件过多");
      return result;
    }
  }

  public boolean directory(String path) throws Exception {
    check();
    return Files.isDirectory(find(path, false), LinkOption.NOFOLLOW_LINKS);
  }

  public byte[] read(String path, int max) throws Exception {
    check();
    Path p = find(path, false);
    if (Files.size(p) > max) throw new IOException("读回文件过大");
    return DesktopResources.limited(Files.newInputStream(p), max);
  }

  public void mkdir(String path) throws Exception {
    mutable();
    Files.createDirectory(find(path, true));
  }

  public void create(String path, byte[] bytes) throws Exception {
    mutable();
    Path p = find(path, true);
    try (FileChannel channel =
        FileChannel.open(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(bytes);
      while (b.hasRemaining()) channel.write(b);
      channel.force(true);
    }
    if (!Arrays.equals(bytes, read(path, Math.max(bytes.length, 1))))
      throw new IOException("完整读回不一致");
  }

  public void delete(String path) throws Exception {
    mutable();
    Path p = find(path, false);
    if (Files.isDirectory(p)) throw new IOException("不会自动删除目录");
    Files.delete(p);
  }

  public void rename(String path, String name) throws Exception {
    mutable();
    if (!name.matches("[A-Za-z0-9_.-]+")) throw new IOException("目标名称无效");
    Path p = find(path, false), target = p.getParent().resolve(name);
    if (Files.exists(target)) throw new IOException("目标文件已存在");
    try {
      Files.move(p, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(p, target);
    }
    check();
  }
}
