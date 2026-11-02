package local.grworld.studio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Only pinned, hash-verified upstream assets, never an arbitrary user ZIP. */
final class EnhancementBundle {
  static final String VERSION = "1.3.5",
      START = "SCRIPT/STARTUP.TTL",
      LIST = "SCRIPT/GRKIT/PACKAGE.TXT";
  final String family;
  final LinkedHashMap<String, byte[]> all = new LinkedHashMap<>();
  final byte[] license;

  static String digest(String f) throws IOException {
    switch (f) {
      case "gr3":
        return "b97e9aa3817c2e9a1cc00d538c8f8a0df188c055674fe2a432f544a4a26071ae";
      case "gr3x":
        return "73efffa80db1078c09f727155a459724e4b6e92b8daa8fbca461bf5d1d5ffaf3";
      case "gr4":
        return "99401ae517bb4cf702a8a97852fedc0e13e22b899e96791ab84a47f00fb605a9";
      default:
        throw new IOException("没有匹配的拍摄增强包");
    }
  }

  EnhancementBundle(String family, byte[] zip) throws Exception {
    this.family = family;
    if (!digest(family).equals(EndScreenPolicy.sha256(zip))) throw new IOException("增强资源校验失败");
    byte[] notices = null;
    Set<String> seen = new HashSet<>();
    try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry e;
      while ((e = z.getNextEntry()) != null) {
        String p = e.getName();
        if (!seen.add(p.toUpperCase(Locale.US)) || p.contains("..") || p.startsWith("/"))
          throw new IOException("增强包路径不明确");
        if (e.isDirectory()) continue;
        byte[] data = read(z, 2 * 1024 * 1024);
        if (p.equals("LICENSES.md")) notices = data;
        if (!p.startsWith("SD_ROOT/")) continue;
        String path = p.substring(8).toUpperCase(Locale.US);
        all.put(path, data);
      }
    }
    if (notices == null
        || !all.containsKey(START)
        || !all.containsKey(LIST)
        || !all.containsKey("SCRIPT/GRKIT/MODULES/FRAMING.GRM")
        || !all.containsKey("SCRIPT/GRKIT/MODULES/GUIDES.GRM")) throw new IOException("增强包内容不完整");
    license = notices;
  }

  static byte[] read(InputStream in, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] b = new byte[8192];
    int n;
    while ((n = in.read(b)) != -1) {
      if (out.size() + n > max) throw new IOException("文件超过限制");
      out.write(b, 0, n);
    }
    return out.toByteArray();
  }

  String entry() {
    for (String p : all.keySet()) if (p.matches("[0-9]{8}\\.[0-9]{3}")) return p;
    throw new IllegalStateException("缺少机型入口");
  }

  LinkedHashMap<String, byte[]> files(boolean framing, boolean guides, byte[] existingList)
      throws Exception {
    return files(EnhancementSelection.basic(framing, guides), existingList);
  }

  LinkedHashMap<String, byte[]> files(EnhancementSelection choice, byte[] existingList)
      throws Exception {
    if (choice.modules().isEmpty()) throw new IOException("请至少选择一个模块，移除请使用管理入口");
    if (choice.film && choice.effects.isEmpty()) throw new IOException("启用胶片模拟时至少选择一个风格");
    LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
    for (Map.Entry<String, byte[]> e : all.entrySet()) {
      String p = e.getKey();
      if (p.equals(LIST) || p.equals(START)) continue;
      if (p.startsWith("SCRIPT/GRKIT/MODULES/")
          && !choice.modules().contains(p.substring(p.lastIndexOf('/') + 1))) continue;
      if (p.startsWith("SCRIPT/GRKIT/EFFECTS/")
          && (!choice.film || !choice.effects.contains(p.substring(p.lastIndexOf('/') + 1))))
        continue;
      files.put(p, e.getValue());
    }
    TreeSet<String> names = list(existingList);
    for (String name : new String[] {"FRAMING.GRM", "GUIDES.GRM"}) names.remove(name);
    if (choice.manageColor) {
      names.remove("FILMLOOK.GRM");
      names.remove("DATE.GRM");
      names.removeIf(n -> n.endsWith(".GRF") && all.containsKey("SCRIPT/GRKIT/EFFECTS/" + n));
    }
    for (String name : choice.modules()) {
      if (!all.containsKey("SCRIPT/GRKIT/MODULES/" + name)) throw new IOException("缺少模块：" + name);
      names.add(name);
    }
    if (choice.film)
      for (String name : choice.effects) {
        if (!all.containsKey("SCRIPT/GRKIT/EFFECTS/" + name))
          throw new IOException("缺少胶片风格：" + name);
        names.add(name);
      }
    if (names.stream().filter(n -> n.endsWith(".GRF")).count() > 40)
      throw new IOException("合并后的滤镜数量超过机内上限 40");
    StringBuilder text = new StringBuilder();
    for (String n : names) text.append(n).append('\n');
    files.put(LIST, text.toString().getBytes(StandardCharsets.US_ASCII));
    files.put(START, all.get(START));
    return files;
  }

  static TreeSet<String> list(byte[] bytes) throws IOException {
    TreeSet<String> names = new TreeSet<>();
    if (bytes == null) return names;
    if (bytes.length > 8192) throw new IOException("模块清单过长");
    for (String name : new String(bytes, StandardCharsets.US_ASCII).trim().split("\\s+")) {
      if (name.isEmpty()) continue;
      if (!name.matches("[0-9A-Z_]{1,8}\\.(GRM|GRF)") || !names.add(name))
        throw new IOException("模块清单无法识别，不会覆盖");
    }
    return names;
  }

  byte[] manual(boolean framing, boolean guides) throws Exception {
    return manual(EnhancementSelection.basic(framing, guides));
  }

  byte[] manual(EnhancementSelection choice) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(out)) {
      for (Map.Entry<String, byte[]> e : files(choice, null).entrySet()) {
        z.putNextEntry(new ZipEntry("SD_ROOT/" + e.getKey()));
        z.write(e.getValue());
        z.closeEntry();
      }
      z.putNextEntry(new ZipEntry("LICENSES.md"));
      z.write(license);
      z.closeEntry();
      z.putNextEntry(new ZipEntry("使用说明.txt"));
      z.write(
          ("仅用于 "
                  + family
                  + " 支持的官方固件。关闭相机，把 SD_ROOT 内的内容复制到卡根目录。\r\n"
                  + "不要覆盖其他 startup.ttl 或已有 GRKit；已有内容请使用 App 的直接写卡合并检查。\r\n"
                  + "首次按住 MENU 开机启用 Script，等待自动关机，再正常开机。不要保留根目录固件 BIN。\r\n"
                  + "等待 GRKit 加载，ADJ 第六项 GRK 中启用："
                  + choice.description()
                  + "。胶片风格与日期样式在机内 GRK / Fn 菜单选择。\r\n"
                  + "留卡使用，每次开机重新加载。文件准备不等于机内成功，请实际试拍并核对。\r\n"
                  + "本手动包没有 App 所有权记录；移除请按上游安装指南，不要删除其他工具文件。\r\n"
                  + "https://github.com/ahonn/GRKit/tree/v1.3.5\r\n")
              .getBytes(StandardCharsets.UTF_8));
      z.closeEntry();
    }
    return out.toByteArray();
  }
}
