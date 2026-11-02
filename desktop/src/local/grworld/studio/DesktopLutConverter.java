package local.grworld.studio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Runs the pinned GRKit WASM fitter locally, as a bounded offline child process. */
final class DesktopLutConverter {
  static Path directory() throws Exception {
    Path jar =
        Path.of(
            DesktopLutConverter.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    return jar.resolveSibling("converter");
  }

  static JSONObject run(Path job) throws Exception {
    Path directory = directory();
    ProcessBuilder builder =
        new ProcessBuilder(
            directory.resolve("node.exe").toString(),
            "--no-warnings",
            "--max-old-space-size=512",
            directory.resolve("import-effect.mjs").toString(),
            job.toString());
    builder.directory(directory.toFile());
    builder.environment().remove("NODE_OPTIONS");
    builder.environment().remove("NODE_PATH");
    builder.redirectOutput(job.resolve("stdout.txt").toFile());
    builder.redirectError(job.resolve("stderr.txt").toFile());
    Process process = builder.start();
    try {
      if (!process.waitFor(180, TimeUnit.SECONDS))
        throw new IOException("滤镜转换超时，未加入滤镜库。请使用较小的 LUT 重试。");
      Path file = job.resolve("result.json");
      if (!Files.isRegularFile(file) || Files.size(file) > 65536)
        throw new IOException("滤镜转换没有返回有效结果");
      JSONObject result = new JSONObject(Files.readString(file));
      if (process.exitValue() != 0 || !result.optBoolean("ok"))
        throw new IOException(result.optString("error", "滤镜无法转换"));
      return result;
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  static void clean(Path job, Path parent) throws IOException {
    if (!job.toAbsolutePath().normalize().getParent().equals(parent.toAbsolutePath().normalize())
        || !job.getFileName().toString().startsWith("lut-job-")) throw new IOException("临时目录范围无效");
    try (var paths = Files.walk(job)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }

  static DesktopEffects.Effect convert(
      DesktopStore store, CameraIdentity id, Path source, String name, String input)
      throws Exception {
    if (!name.matches("[\\x20-\\x7e]{1,16}") || !name.equals(name.trim()))
      throw new IOException("相机风格名需为 1～16 个英文、数字或空格");
    byte[] bytes = DesktopResources.limited(Files.newInputStream(source), 32 * 1024 * 1024);
    String sourceName = source.getFileName().toString();
    String effectId =
        "user-"
            + EndScreenPolicy.sha256(
                    concat(bytes, (name + "/" + input).getBytes(StandardCharsets.UTF_8)))
                .substring(0, 24);
    Path job = Files.createTempDirectory(store.root, "lut-job-");
    try {
      Files.write(job.resolve("input.lut"), bytes);
      Files.writeString(
          job.resolve("request.json"),
          new JSONObject()
              .put("kind", "import")
              .put("target", DesktopEffects.target(id))
              .put("source", sourceName)
              .put("input", input)
              .put("id", effectId)
              .put("name", name)
              .toString());
      JSONObject result = run(job);
      byte[] blob = DesktopResources.limited(Files.newInputStream(job.resolve("effect.grf")), 8704);
      DesktopEffects.Effect effect = DesktopEffects.decode(blob);
      if (!effect.id.equals(effectId) || !effect.name.equals(name))
        throw new IOException("转换结果与导入任务不匹配");
      effect.preview =
          DesktopResources.limited(
              Files.newInputStream(job.resolve("preview.lut")), DesktopEffects.TABLE_BYTES);
      if (effect.preview.length != DesktopEffects.TABLE_BYTES) throw new IOException("滤镜预览表不完整");
      effect.grade = result.getString("grade");
      if (!effect.grade.matches("[ABCD]")) throw new IOException("滤镜拟合结果无效");
      effect.input = result.getString("input");
      effect.source = sourceName;
      effect.custom = true;
      JSONArray warnings = result.getJSONArray("warnings");
      for (int n = 0; n < warnings.length(); n++) effect.warnings.add(warnings.getString(n));
      return effect;
    } finally {
      clean(job, store.root);
    }
  }

  static byte[] concat(byte[] a, byte[] b) {
    byte[] all = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, all, a.length, b.length);
    return all;
  }

  static byte[] hald(DesktopStore store) throws Exception {
    Path job = Files.createTempDirectory(store.root, "lut-job-");
    try {
      Files.writeString(job.resolve("request.json"), "{\"kind\":\"hald\"}");
      run(job);
      return DesktopResources.limited(
          Files.newInputStream(job.resolve("hald.png")), 32 * 1024 * 1024);
    } finally {
      clean(job, store.root);
    }
  }
}
