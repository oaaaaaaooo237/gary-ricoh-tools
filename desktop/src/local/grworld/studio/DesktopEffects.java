package local.grworld.studio;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import org.json.*;

/** A local, data-only effect library. Module binaries always come from the pinned bundle. */
final class DesktopEffects {
  static final int TABLE_BYTES = 33 * 33 * 33 * 3;
  final DesktopStore store;

  DesktopEffects(DesktopStore store) {
    this.store = store;
  }

  static String target(CameraIdentity id) throws IOException {
    String family = EnhancementPolicy.family(id);
    if (family == null) throw new IOException("当前机型和固件不支持胶片模块");
    return family.equals("gr4") ? "gr4-" + id.firmware : "gr3-2.10";
  }

  static final class Effect {
    String id, name, file, origin = "", license = "", grade = "", input = "", source = "";
    boolean custom;
    byte[] blob, preview;
    List<String> warnings = new ArrayList<>();

    public String toString() {
      return name + (custom ? " · 自定义" : "");
    }
  }

  static String ascii(byte[] bytes, int offset) throws IOException {
    int end = offset;
    while (end < offset + 32 && bytes[end] != 0) end++;
    if (end == offset + 32) throw new IOException("滤镜名称未结束");
    for (int n = offset; n < offset + 32; n++)
      if ((n >= end && bytes[n] != 0) || (n < end && (bytes[n] < 32 || bytes[n] > 126)))
        throw new IOException("滤镜名称格式错误");
    return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
  }

  static Effect decode(byte[] bytes) throws Exception {
    if ((bytes.length != 8192 && bytes.length != 8704)
        || !new String(bytes, 0, 8, StandardCharsets.US_ASCII).equals("GRFX0001"))
      throw new IOException("不是完整的 GRKit 色彩效果文件");
    ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    CRC32 crc = new CRC32();
    crc.update(bytes, 0, 12);
    crc.update(new byte[4]);
    crc.update(bytes, 16, bytes.length - 16);
    if (b.getInt(8) != bytes.length || Integer.toUnsignedLong(b.getInt(12)) != crc.getValue())
      throw new IOException("滤镜长度或 CRC 校验失败");
    int features = b.getInt(84);
    if ((b.getInt(80) & ~1) != 0
        || (features & ~27) != 0
        || ((features & 16) != 0) != (bytes.length == 8704)
        || ((features & 16) != 0 && (features & 8) == 0)) throw new IOException("滤镜功能标记不受支持");
    for (int at = 148; at < 1684; at += 2)
      if (Short.toUnsignedInt(b.getShort(at)) > 16383) throw new IOException("滤镜曲线超出范围");
    for (int row = 0; row < 3; row++) {
      int sum = 0;
      for (int col = 0; col < 3; col++) sum += b.getShort(128 + 2 * (row * 3 + col));
      if (sum != 8192) throw new IOException("滤镜矩阵无效");
    }
    for (int at = 1687; at < 8084; at += 4)
      if (bytes[at] != 0 && bytes[at] != (byte) 255) throw new IOException("滤镜图标透明度无效");
    Effect effect = new Effect();
    effect.id = ascii(bytes, 16);
    effect.name = ascii(bytes, 48);
    if (!effect.id.matches("[a-z0-9][a-z0-9-]{0,30}")
        || !effect.name.matches("[\\x20-\\x7e]{1,16}")
        || !effect.name.equals(effect.name.trim())) throw new IOException("滤镜标识无效");
    effect.file =
        EndScreenPolicy.sha256(effect.id.getBytes(StandardCharsets.US_ASCII))
                .substring(0, 8)
                .toUpperCase(Locale.ROOT)
            + ".GRF";
    effect.blob = bytes.clone();
    return effect;
  }

  JSONArray index() throws Exception {
    return store.exists("effects.json")
        ? new JSONArray(new String(store.read("effects.json"), StandardCharsets.UTF_8))
        : new JSONArray();
  }

  static String prefix(String target, String file) throws IOException {
    if (!target.matches("gr[34]-[0-9.]+(?:-0528)?") || !file.matches("[0-9A-F]{8}\\.GRF"))
      throw new IOException("滤镜索引路径无效");
    return "effect-" + target + "-" + file.substring(0, 8);
  }

  List<Effect> list(CameraIdentity id, EnhancementBundle bundle) throws Exception {
    String target = target(id);
    Path folder = DesktopLutConverter.directory().resolve("catalog").resolve(target);
    JSONObject catalog = new JSONObject(Files.readString(folder.resolve("catalog.json")));
    List<Effect> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    JSONArray entries = catalog.getJSONArray("effects");
    for (int n = 0; n < entries.length(); n++) {
      JSONObject entry = entries.getJSONObject(n);
      String file = entry.getString("file");
      byte[] data = bundle.all.get("SCRIPT/GRKIT/EFFECTS/" + file);
      if (data == null) continue;
      Effect effect = decode(data);
      if (!effect.file.equals(file)
          || !EndScreenPolicy.sha256(data).equals(entry.getString("sha256")))
        throw new IOException("内置胶片风格与资源包不一致");
      String preview = entry.getString("preview");
      if (!preview.matches("preview/[A-Za-z0-9_-]+\\.lut")) throw new IOException("预览资源路径无效");
      effect.preview = Files.readAllBytes(folder.resolve(preview));
      if (effect.preview.length != TABLE_BYTES) throw new IOException("胶片预览表不完整");
      effect.origin = entry.optString("origin");
      effect.license = entry.optString("license");
      result.add(effect);
      seen.add(effect.file);
    }
    JSONArray saved = index();
    for (int n = 0; n < saved.length(); n++) {
      JSONObject item = saved.getJSONObject(n);
      if (!item.getString("target").equals(target)) continue;
      String file = item.getString("file"), key = prefix(target, file);
      byte[] bytes = store.read(key + ".grf");
      if (!EndScreenPolicy.sha256(bytes).equals(item.getString("sha256")))
        throw new IOException("本地自定义滤镜已改变");
      Effect effect = decode(bytes);
      if (!effect.file.equals(file) || !seen.add(file)) throw new IOException("滤镜标识冲突");
      effect.preview = store.read(key + ".lut");
      if (effect.preview.length != TABLE_BYTES
          || !EndScreenPolicy.sha256(effect.preview).equals(item.getString("preview_sha256")))
        throw new IOException("自定义滤镜预览校验失败");
      effect.custom = true;
      effect.grade = item.getString("grade");
      effect.source = item.getString("source");
      effect.input = item.getString("input");
      effect.origin = "本地导入：" + effect.source;
      JSONArray warnings = item.getJSONArray("warnings");
      for (int w = 0; w < warnings.length(); w++) effect.warnings.add(warnings.getString(w));
      result.add(effect);
      bundle.all.put("SCRIPT/GRKIT/EFFECTS/" + file, bytes);
    }
    result.sort(Comparator.comparing(e -> e.name));
    return result;
  }

  void save(CameraIdentity id, Effect effect) throws Exception {
    String target = target(id), key = prefix(target, effect.file);
    JSONArray entries = index();
    for (int n = 0; n < entries.length(); n++) {
      JSONObject prior = entries.getJSONObject(n);
      if (prior.getString("target").equals(target) && prior.getString("file").equals(effect.file)) {
        if (prior.getString("sha256").equals(EndScreenPolicy.sha256(effect.blob))) return;
        throw new IOException("同名滤镜内容不同，请改名重新导入");
      }
    }
    if (entries.length() >= 100) throw new IOException("本地滤镜库已达到 100 个，请先整理");
    store.write(key + ".grf", effect.blob);
    store.write(key + ".lut", effect.preview);
    entries.put(
        new JSONObject()
            .put("target", target)
            .put("file", effect.file)
            .put("sha256", EndScreenPolicy.sha256(effect.blob))
            .put("preview_sha256", EndScreenPolicy.sha256(effect.preview))
            .put("grade", effect.grade)
            .put("source", effect.source)
            .put("input", effect.input)
            .put("warnings", new JSONArray(effect.warnings)));
    store.write("effects.json", entries.toString().getBytes(StandardCharsets.UTF_8));
  }
}
