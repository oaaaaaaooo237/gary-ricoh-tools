package local.grworld.studio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared card transaction. Unknown scripts never get chained or overwritten. */
final class EnhancementDeployment {
  static final String RECORD = "GRSTUDIO.TXT", PAUSED = "GRSTART.TTL", TEMP = "SCRIPT/GRSTAGE.TMP";

  static boolean exists(SdCardDeployment.Card c, String p) throws Exception {
    return SdCardDeployment.exists(c, p);
  }

  static byte[] bytes(SdCardDeployment.Card c, String p) throws Exception {
    return c.read(p, 2 * 1024 * 1024);
  }

  static boolean equal(SdCardDeployment.Card c, String p, byte[] data) throws Exception {
    return exists(c, p) && Arrays.equals(bytes(c, p), data);
  }

  static final class Receipt {
    final Map<String, String> hashes = new LinkedHashMap<>();
    final Set<String> owned = new HashSet<>();
    byte[] baseline;
  }

  static Receipt receipt(SdCardDeployment.Card c, EnhancementBundle b) throws Exception {
    if (!exists(c, RECORD)) return null;
    String[] lines = new String(c.read(RECORD, 16384), StandardCharsets.US_ASCII).split("\n");
    if (lines.length < 3 || !lines[0].equals("GRSTUDIO 1") || !lines[1].equals(b.family))
      throw new IOException("增强任务记录与当前机型不匹配");
    Receipt r = new Receipt();
    if (!lines[2].equals("-")) {
      try {
        r.baseline = Base64.getDecoder().decode(lines[2]);
        EnhancementBundle.list(r.baseline);
      } catch (Exception e) {
        throw new IOException("原模块清单记录不完整");
      }
    }
    for (int i = 3; i < lines.length; i++) {
      String[] fields = lines[i].split("\t");
      if (fields.length != 3
          || !b.all.containsKey(fields[0])
          || !fields[1].matches("[0-9a-f]{64}")
          || !fields[2].matches("[01]")
          || r.hashes.put(fields[0], fields[1]) != null) throw new IOException("增强文件记录无法核实");
      if (!fields[0].equals(EnhancementBundle.LIST)
          && !fields[1].equals(EndScreenPolicy.sha256(b.all.get(fields[0]))))
        throw new IOException("增强文件版本不受支持");
      if (fields[2].equals("1")) r.owned.add(fields[0]);
    }
    if (!r.hashes.containsKey(EnhancementBundle.START)
        || !r.hashes.containsKey(EnhancementBundle.LIST)) throw new IOException("增强所有权记录缺失");
    return r;
  }

  static void verifyReceipt(SdCardDeployment.Card c, Receipt r, boolean paused) throws Exception {
    for (Map.Entry<String, String> e : r.hashes.entrySet()) {
      if (paused && e.getKey().equals(EnhancementBundle.START)) continue;
      if (!exists(c, e.getKey())
          || !e.getValue().equals(EndScreenPolicy.sha256(bytes(c, e.getKey()))))
        throw new IOException("增强文件已改变，不自动覆盖或删除：" + e.getKey());
    }
  }

  static final class Inspection {
    EnhancementSelection choice;
    final Map<String, String> snapshot = new LinkedHashMap<>();
    final String key;
    final Receipt receipt;
    final LinkedHashMap<String, byte[]> files;
    final boolean remove;

    Inspection(String key, Receipt r, LinkedHashMap<String, byte[]> files, boolean remove) {
      this.key = key;
      receipt = r;
      this.files = files;
      this.remove = remove;
    }
  }

  static Inspection inspect(
      SdCardDeployment.Card c, EnhancementBundle b, boolean framing, boolean guides, boolean remove)
      throws Exception {
    return inspect(c, b, EnhancementSelection.basic(framing, guides), remove);
  }

  static Inspection inspect(
      SdCardDeployment.Card c, EnhancementBundle b, EnhancementSelection choice, boolean remove)
      throws Exception {
    c.check();
    if (exists(c, PAUSED) || exists(c, "GR3DEP.TXT"))
      throw new IOException("有尚未结束的 SD 换图／暂停任务，请先完成或恢复该任务");
    if (exists(c, TEMP)) throw new IOException("存在未完成的增强临时文件，请先检查，不自动覆盖");
    for (String name : c.list("")) {
      if (name.toLowerCase(Locale.US).endsWith(".bin"))
        throw new IOException("卡根目录存在固件 BIN，请先移走再准备增强");
      if (name.matches("[0-9]{8}\\.[0-9]{3}") && !name.equalsIgnoreCase(b.entry()))
        throw new IOException("卡上有其他机型入口，请检查目标卡");
    }
    Receipt r = receipt(c, b);
    if (r != null) verifyReceipt(c, r, false);
    if (remove && r == null) throw new IOException("没有本工具可验证的部署记录，不自动删除现有 GRKit");
    if (exists(c, EnhancementBundle.START)
        && !equal(c, EnhancementBundle.START, b.all.get(EnhancementBundle.START)))
      throw new IOException("卡里有其他启动脚本，不会覆盖");
    byte[] list = exists(c, EnhancementBundle.LIST) ? bytes(c, EnhancementBundle.LIST) : null;
    if (list != null && !exists(c, EnhancementBundle.START))
      throw new IOException("已有不完整 GRKit，请先检查启动文件");
    for (String n : EnhancementBundle.list(list)) {
      String p = "SCRIPT/GRKIT/" + (n.endsWith(".GRM") ? "MODULES/" : "EFFECTS/") + n;
      if (!exists(c, p)) throw new IOException("已有模块清单缺少文件：" + n);
    }
    LinkedHashMap<String, byte[]> files = remove ? new LinkedHashMap<>() : b.files(choice, list);
    for (Map.Entry<String, byte[]> e : files.entrySet())
      if (!e.getKey().equals(EnhancementBundle.LIST)
          && exists(c, e.getKey())
          && !equal(c, e.getKey(), e.getValue()))
        throw new IOException("已有不同版本文件，保留原内容：" + e.getKey());
    Inspection i = new Inspection(c.key(), r, files, remove);
    i.choice = choice;
    Set<String> paths = new TreeSet<>(b.all.keySet());
    paths.add(RECORD);
    paths.add(PAUSED);
    paths.add(TEMP);
    for (String p : paths)
      i.snapshot.put(p, !exists(c, p) ? "-" : EndScreenPolicy.sha256(bytes(c, p)));
    return i;
  }

  static void compare(Inspection a, Inspection b) throws IOException {
    if (!a.key.equals(b.key) || !a.snapshot.equals(b.snapshot) || a.remove != b.remove)
      throw new IOException("卡片或文件在确认后发生变化，请重新检查");
  }

  static void parents(SdCardDeployment.Card c, String p, SdCardDeployment.Gate gate)
      throws Exception {
    int slash = p.indexOf('/');
    while (slash > 0) {
      String dir = p.substring(0, slash);
      if (!exists(c, dir)) {
        gate.check();
        c.mkdir(dir);
      } else if (!c.directory(dir)) throw new IOException("所需目录被文件占用：" + dir);
      slash = p.indexOf('/', slash + 1);
    }
  }

  static void put(SdCardDeployment.Card c, String p, byte[] data, SdCardDeployment.Gate gate)
      throws Exception {
    gate.check();
    c.check();
    parents(c, p, gate);
    if (exists(c, p)) {
      if (equal(c, p, data)) return;
      c.delete(p);
    }
    c.create(p, data);
    if (!equal(c, p, data)) throw new IOException("完整读回失败：" + p);
  }

  static byte[] record(Receipt r, String family) {
    StringBuilder out =
        new StringBuilder(
            "GRSTUDIO 1\n"
                + family
                + "\n"
                + (r.baseline == null ? "-" : Base64.getEncoder().encodeToString(r.baseline))
                + "\n");
    for (Map.Entry<String, String> e : r.hashes.entrySet())
      out.append(e.getKey())
          .append('\t')
          .append(e.getValue())
          .append('\t')
          .append(r.owned.contains(e.getKey()) ? '1' : '0')
          .append('\n');
    return out.toString().getBytes(StandardCharsets.US_ASCII);
  }

  static void apply(
      SdCardDeployment.Card c,
      EnhancementBundle b,
      boolean f,
      boolean g,
      Inspection reviewed,
      SdCardDeployment.Gate gate)
      throws Exception {
    apply(c, b, EnhancementSelection.basic(f, g), reviewed, gate);
  }

  static void apply(
      SdCardDeployment.Card c,
      EnhancementBundle b,
      EnhancementSelection choice,
      Inspection reviewed,
      SdCardDeployment.Gate gate)
      throws Exception {
    gate.check();
    if (reviewed.choice == null
        || reviewed.choice.manageColor != choice.manageColor
        || !reviewed.choice.modules().equals(choice.modules())
        || !reviewed.choice.effects.equals(choice.effects))
      throw new IOException("模块选择在确认后改变，请重新核对");
    compare(reviewed, inspect(c, b, choice, reviewed.remove));
    LinkedHashMap<String, byte[]> before = new LinkedHashMap<>();
    Set<String> paths = new LinkedHashSet<>(reviewed.files.keySet());
    paths.add(RECORD);
    paths.add(EnhancementBundle.START);
    paths.add(TEMP);
    if (reviewed.receipt != null) paths.addAll(reviewed.receipt.hashes.keySet());
    for (String p : paths) before.put(p, exists(c, p) ? bytes(c, p) : null);
    try {
      if (exists(c, EnhancementBundle.START)) {
        gate.check();
        c.delete(EnhancementBundle.START);
      }
      if (reviewed.remove) {
        Receipt r = reviewed.receipt;
        for (String p : r.owned) {
          gate.check();
          if (exists(c, p)) c.delete(p);
        }
        if (r.baseline != null) put(c, EnhancementBundle.LIST, r.baseline, gate);
        if (!r.owned.contains(EnhancementBundle.START))
          put(c, TEMP, b.all.get(EnhancementBundle.START), gate);
        gate.check();
        c.delete(RECORD);
        if (exists(c, TEMP)) {
          gate.check();
          c.rename(TEMP, "STARTUP.TTL");
        }
        for (String p : r.owned) if (exists(c, p)) throw new IOException("增强自有文件未移除：" + p);
        if (r.baseline != null && !equal(c, EnhancementBundle.LIST, r.baseline))
          throw new IOException("原有模块清单恢复失败");
      } else {
        Receipt r = new Receipt();
        if (reviewed.receipt != null) {
          r.hashes.putAll(reviewed.receipt.hashes);
          r.owned.addAll(reviewed.receipt.owned);
          r.baseline = reviewed.receipt.baseline;
        } else r.baseline = before.get(EnhancementBundle.LIST);
        for (Map.Entry<String, byte[]> e : reviewed.files.entrySet()) {
          String p = e.getKey();
          if (!exists(c, p) && before.get(p) == null) r.owned.add(p);
          r.hashes.put(p, EndScreenPolicy.sha256(e.getValue()));
          if (!p.equals(EnhancementBundle.START)) put(c, p, e.getValue(), gate);
        }
        put(c, RECORD, record(r, b.family), gate);
        put(c, TEMP, b.all.get(EnhancementBundle.START), gate);
        gate.check();
        c.check();
        c.rename(TEMP, "STARTUP.TTL");
        verifyReceipt(c, r, false);
      }
      gate.check();
      c.check();
    } catch (Exception error) {
      boolean restored = false;
      try {
        if (exists(c, EnhancementBundle.START)
            && equal(c, EnhancementBundle.START, b.all.get(EnhancementBundle.START)))
          c.delete(EnhancementBundle.START);
        for (Map.Entry<String, byte[]> e : before.entrySet()) {
          String p = e.getKey();
          if (p.equals(EnhancementBundle.START)) continue;
          if (e.getValue() == null) {
            if (exists(c, p)) c.delete(p);
          } else put(c, p, e.getValue(), () -> {});
        }
        byte[] original = before.get(EnhancementBundle.START);
        if (original != null) {
          put(c, TEMP, original, () -> {});
          c.rename(TEMP, "STARTUP.TTL");
        }
        for (Map.Entry<String, byte[]> e : before.entrySet())
          if (e.getValue() == null ? exists(c, e.getKey()) : !equal(c, e.getKey(), e.getValue()))
            throw new IOException("回退不完整");
        restored = true;
      } catch (Exception ignored) {
      }
      throw new IOException(
          "增强卡片操作未完成。"
              + (restored ? "原启动文件已恢复并核验。" : "回退尚未核实，不要把此卡插回相机执行，请先检查启动文件。")
              + "\n"
              + error.getMessage(),
          error);
    }
  }

  static boolean paused(SdCardDeployment.Card c, EnhancementBundle b) throws Exception {
    return equal(c, PAUSED, b.all.get(EnhancementBundle.START));
  }

  static boolean active(SdCardDeployment.Card c, EnhancementBundle b) throws Exception {
    return exists(c, RECORD)
        && equal(c, EnhancementBundle.START, b.all.get(EnhancementBundle.START));
  }

  static Inspection inspectResume(SdCardDeployment.Card c, EnhancementBundle b) throws Exception {
    c.check();
    if (!paused(c, b)
        || exists(c, "GR3DEP.TXT")
        || exists(c, EnhancementBundle.START)
        || exists(c, TEMP)) throw new IOException("仍有任务占用启动入口，或暂停文件无法核实，请先完成换图任务");
    Receipt r = receipt(c, b);
    if (r == null) throw new IOException("增强所有权记录缺失");
    verifyReceipt(c, r, true);
    Inspection i = new Inspection(c.key(), r, new LinkedHashMap<>(), false);
    Set<String> paths = new TreeSet<>(r.hashes.keySet());
    paths.add(PAUSED);
    paths.add(RECORD);
    paths.add("GR3DEP.TXT");
    paths.add(TEMP);
    for (String p : paths)
      i.snapshot.put(p, !exists(c, p) ? "-" : EndScreenPolicy.sha256(bytes(c, p)));
    return i;
  }

  /** Inspection-only view: the known enhancer entry will be suspended after explicit approval. */
  static SdCardDeployment.Card withoutStartup(final SdCardDeployment.Card card) {
    return new SdCardDeployment.Card() {
      public String key() {
        return card.key();
      }

      public void check() throws Exception {
        card.check();
      }

      public List<String> list(String path) throws Exception {
        List<String> names = new ArrayList<>(card.list(path));
        if (path.equalsIgnoreCase("script")) names.removeIf(n -> n.equalsIgnoreCase("startup.ttl"));
        return names;
      }

      public boolean directory(String p) throws Exception {
        return card.directory(p);
      }

      public byte[] read(String p, int max) throws Exception {
        return card.read(p, max);
      }

      public void mkdir(String p) throws Exception {
        throw new IOException("检查阶段只读");
      }

      public void create(String p, byte[] b) throws Exception {
        throw new IOException("检查阶段只读");
      }

      public void delete(String p) throws Exception {
        throw new IOException("检查阶段只读");
      }

      public void rename(String p, String n) throws Exception {
        throw new IOException("检查阶段只读");
      }
    };
  }

  static void suspend(SdCardDeployment.Card c, EnhancementBundle b, SdCardDeployment.Gate gate)
      throws Exception {
    Receipt r = receipt(c, b);
    if (r == null) throw new IOException("此 GRKit 尚无本工具记录，请先在拍摄增强中检查接管");
    if (paused(c, b)) {
      verifyReceipt(c, r, true);
      return;
    }
    verifyReceipt(c, r, false);
    if (!equal(c, EnhancementBundle.START, b.all.get(EnhancementBundle.START)))
      throw new IOException("无法核实增强启动入口");
    put(c, PAUSED, b.all.get(EnhancementBundle.START), gate);
    gate.check();
    c.delete(EnhancementBundle.START);
    if (exists(c, EnhancementBundle.START)) throw new IOException("增强启动入口没有暂停");
  }

  static void resume(SdCardDeployment.Card c, EnhancementBundle b, SdCardDeployment.Gate gate)
      throws Exception {
    if (!exists(c, PAUSED)) return;
    if (!paused(c, b)) throw new IOException("暂停入口被改动，不自动恢复");
    Receipt r = receipt(c, b);
    if (r == null) throw new IOException("增强部署记录缺失");
    verifyReceipt(c, r, true);
    if (exists(c, EnhancementBundle.START)) throw new IOException("启动入口仍被任务占用，增强尚未恢复");
    put(c, TEMP, b.all.get(EnhancementBundle.START), gate);
    gate.check();
    c.rename(TEMP, "STARTUP.TTL");
    if (!equal(c, EnhancementBundle.START, b.all.get(EnhancementBundle.START)))
      throw new IOException("增强入口恢复读回失败");
    gate.check();
    c.delete(PAUSED);
  }
}
