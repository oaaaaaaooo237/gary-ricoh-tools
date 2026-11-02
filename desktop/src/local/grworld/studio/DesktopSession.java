package local.grworld.studio;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import local.grworld.studio.spi.*;
import org.json.JSONObject;

/** Application workflows depend on platform contracts, not JavaFX controls. */
final class DesktopSession {
  final CameraTransport transport;
  final DesktopStore store;
  final DesktopResources resources = new DesktopResources();
  final DesktopEffects effects;
  final DesktopImages images = new DesktopImages();
  final Path fixtureRoot;
  Properties state;
  CameraIdentity current, sessionIdentity;
  String network = "", connectionIssue = "";
  long checkedAt;
  boolean wifiRechecked, sdVerified, enhCardChecked, enhReportChecked;
  byte[] source, prepared;
  BufferedImage original;
  String imageName = "";
  ImageProcessor.Edit edit = new ImageProcessor.Edit("fit", 0, 1, 0, 0, 92);
  Gr3SdSession job;
  SdReconciler.Result sdResult;
  boolean sdCopy;
  String sdMessage = "", enhMessage = "";

  DesktopSession(CameraTransport transport, DesktopStore store, Path fixtureRoot) throws Exception {
    this.transport = transport;
    this.store = store;
    effects = new DesktopEffects(store);
    resources.effects = effects;
    this.fixtureRoot = fixtureRoot;
    state = store.properties("state.properties");
    if (store.exists("image-current.jpg"))
      try {
        prepared = store.read("image-current.jpg");
        EndScreenPolicy.image(prepared);
        source = store.exists("image-source.image") ? store.read("image-source.image") : prepared;
        original = images.decode(source);
        imageName = "上次准备的图片";
        edit =
            new ImageProcessor.Edit(
                state.getProperty("image.mode", "fit"),
                Integer.parseInt(state.getProperty("image.background", "0")),
                Double.parseDouble(state.getProperty("image.zoom", "1")),
                Double.parseDouble(state.getProperty("image.panx", "0")),
                Double.parseDouble(state.getProperty("image.pany", "0")),
                Integer.parseInt(state.getProperty("image.quality", "92")));
      } catch (Exception e) {
        prepared = source = null;
        original = null;
        sdMessage = "上次图片未载入：" + e.getMessage();
      }
    String active = state.getProperty("sd.active", "");
    if (active.matches("[A-F0-9]{6}") && store.exists("sd-" + active + ".gr3sd"))
      try {
        job = Gr3SdSession.load(store.read("sd-" + active + ".gr3sd"), resources::stock);
        if (!job.id.equals(active)) throw new IOException("任务索引与内容不符");
      } catch (Exception e) {
        job = null;
        sdMessage = "历史任务未载入：" + e.getMessage();
      }
  }

  synchronized void persist() throws Exception {
    store.properties("state.properties", state);
  }

  CameraIdentity live() {
    return System.nanoTime() / 1_000_000 - checkedAt < 15000 ? current : null;
  }

  CameraIdentity context() {
    return live() != null ? live() : sessionIdentity;
  }

  synchronized void observe() throws Exception {
    try {
      CameraTransport.Identity r = transport.identify();
      CameraIdentity id = new CameraIdentity(r.model(), r.firmware());
      current = id;
      sessionIdentity = id;
      checkedAt = System.nanoTime() / 1_000_000;
      network = r.networkKey();
      connectionIssue = "";
      if (pending())
        wifiRechecked =
            id.model.equals(state.getProperty("wifi.model"))
                && id.firmware.equals(state.getProperty("wifi.firmware"));
    } catch (Exception e) {
      current = null;
      network = "";
      wifiRechecked = false;
      connectionIssue = e.getMessage();
      throw e;
    }
  }

  void same(CameraIdentity expected) throws Exception {
    CameraIdentity c = context();
    if (c == null || !c.same(expected)) throw new IOException("本次识别的机型或固件已经变化，请重新检查");
  }

  void importImage(byte[] bytes, String name, boolean exact) throws Exception {
    if (pending()) throw new IOException("先核对上次上传的实际结果，再准备下一次操作");
    if (bytes.length > 40 * 1024 * 1024) throw new IOException("图片过大");
    BufferedImage decoded = images.decode(bytes);
    byte[] jpeg;
    if (exact) {
      EndScreenPolicy.image(bytes);
      jpeg = bytes.clone();
    } else jpeg = images.prepare(decoded, new ImageProcessor.Edit("fit", 0, 1, 0, 0, 92));
    source = bytes.clone();
    original = decoded;
    prepared = jpeg;
    imageName = name;
    edit = new ImageProcessor.Edit("fit", 0, 1, 0, 0, 92);
    saveCurrent();
  }

  void process(ImageProcessor.Edit next) throws Exception {
    if (original == null || pending()) throw new IOException("当前不能调整图片");
    byte[] jpeg = images.prepare(original, next);
    prepared = jpeg;
    edit = next;
    saveCurrent();
  }

  void saveCurrent() throws Exception {
    store.write("image-current.jpg", prepared);
    store.write("image-source.image", source);
    state.setProperty("image.mode", edit.mode());
    state.setProperty("image.background", String.valueOf(edit.background()));
    state.setProperty("image.zoom", String.valueOf(edit.zoom()));
    state.setProperty("image.panx", String.valueOf(edit.panX()));
    state.setProperty("image.pany", String.valueOf(edit.panY()));
    state.setProperty("image.quality", String.valueOf(edit.quality()));
    persist();
  }

  void saveLibrary() throws Exception {
    saveLibrary(prepared);
  }

  void saveLibrary(byte[] image) throws Exception {
    if (image == null) throw new IOException("请先选择图片");
    EndScreenPolicy.image(image);
    store.write("library-" + EndScreenPolicy.sha256(image) + ".jpg", image);
  }

  byte[] project() throws Exception {
    return project(prepared);
  }

  byte[] project(byte[] finalJpeg) throws Exception {
    if (source == null || finalJpeg == null) throw new IOException("请先选择图片");
    EndScreenPolicy.image(finalJpeg);
    JSONObject p =
        new JSONObject()
            .put("version", 1)
            .put("name", imageName)
            .put("scope", "local_image_project_not_camera_backup")
            .put("source_sha256", EndScreenPolicy.sha256(source))
            .put("prepared_sha256", EndScreenPolicy.sha256(finalJpeg));
    p.put("mode", List.of("fit", "crop", "stretch", "original").indexOf(edit.mode()))
        .put("zoom", Math.round((edit.zoom() - 1) * 100))
        .put("x", Math.round((edit.panX() + 1) * 100))
        .put("y", Math.round((edit.panY() + 1) * 100))
        .put("quality", edit.quality() - 30)
        .put(
            "background",
            edit.background() == 0x808080 ? 1 : edit.background() == 0xffffff ? 2 : 0);
    LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("project.json", p.toString(2).getBytes(StandardCharsets.UTF_8));
    entries.put("source.image", source);
    entries.put("prepared.jpg", finalJpeg);
    return Gr3SdSession.zip(entries);
  }

  void openProject(byte[] archive) throws Exception {
    if (pending()) throw new IOException("上次上传结果仍待核对");
    if (archive.length > 42 * 1024 * 1024) throw new IOException("工程过大");
    Map<String, byte[]> map = new HashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry e;
      while ((e = zip.getNextEntry()) != null) {
        String n = e.getName();
        if (!Set.of("project.json", "source.image", "prepared.jpg").contains(n)
            || map.containsKey(n)
            || e.isDirectory()) throw new IOException("工程条目不符");
        map.put(
            n,
            readZipEntry(
                zip,
                n.equals("source.image")
                    ? 40 * 1024 * 1024
                    : n.equals("prepared.jpg") ? EndScreenPolicy.MAX_BYTES : 8192));
      }
    }
    if (map.size() != 3) throw new IOException("工程内容不完整");
    JSONObject p = new JSONObject(new String(map.get("project.json"), StandardCharsets.UTF_8));
    if (p.getInt("version") != 1
        || !EndScreenPolicy.sha256(map.get("source.image")).equals(p.getString("source_sha256"))
        || !EndScreenPolicy.sha256(map.get("prepared.jpg")).equals(p.getString("prepared_sha256")))
      throw new IOException("工程哈希不一致");
    EndScreenPolicy.image(map.get("prepared.jpg"));
    BufferedImage decoded = images.decode(map.get("source.image"));
    int mode = p.optInt("mode", 0), background = p.optInt("background", 0);
    if (mode < 0 || mode > 3 || background < 0 || background > 2) throw new IOException("工程图片参数无效");
    ImageProcessor.Edit next =
        new ImageProcessor.Edit(
            new String[] {"fit", "crop", "stretch", "original"}[mode],
            new int[] {0, 0x808080, 0xffffff}[background],
            Math.max(1, Math.min(3, 1 + p.optDouble("zoom", 0) / 100)),
            Math.max(-1, Math.min(1, (p.optDouble("x", 100) - 100) / 100)),
            Math.max(-1, Math.min(1, (p.optDouble("y", 100) - 100) / 100)),
            Math.max(30, Math.min(100, p.optInt("quality", 62) + 30)));
    original = decoded;
    source = map.get("source.image");
    prepared = map.get("prepared.jpg");
    imageName = "已保存工程";
    edit = next;
    saveCurrent();
  }

  static byte[] readZipEntry(ZipInputStream in, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      if (out.size() + n > max) throw new IOException("解压内容超过限制");
      out.write(buf, 0, n);
    }
    return out.toByteArray();
  }

  boolean pending() {
    return Boolean.parseBoolean(state.getProperty("wifi.pending", "false"));
  }

  void send(byte[] image, boolean factory) throws Exception {
    if (pending()) throw new IOException("上次结果仍待核对，不自动重复上传");
    if (!transport.canUpload()) throw new IOException("USB 仅用于只读识别，上传图片需要相机 Wi-Fi");
    CameraIdentity expected = live();
    if (expected == null) throw new IOException("请先连接并识别相机");
    EndScreenPolicy.camera(expected.model, expected.firmware);
    EndScreenPolicy.image(image);
    String net = null;
    for (int i = 0; i < 3; i++) {
      CameraTransport.Identity r = transport.identify();
      if (!transport.canUpload()) throw new IOException("复核时未连接相机 Wi-Fi，未上传图片");
      ConnectionPolicy.reply(r.elapsedMillis());
      if (!expected.same(new CameraIdentity(r.model(), r.firmware())))
        throw new IOException("复核期间机型或固件改变");
      if (net != null && !net.equals(r.networkKey())) throw new IOException("复核期间网络改变");
      net = r.networkKey();
      if (i < 2) Thread.sleep(500);
    }
    state.setProperty("wifi.pending", "true");
    state.setProperty("wifi.id", UUID.randomUUID().toString());
    state.setProperty("wifi.model", expected.model);
    state.setProperty("wifi.firmware", expected.firmware);
    state.setProperty("wifi.hash", EndScreenPolicy.sha256(image));
    state.setProperty("wifi.action", factory ? "factory" : "replace");
    state.setProperty("wifi.status", "outcome_unknown");
    persist();
    wifiRechecked = true;
    transport.upload(image, net);
    state.setProperty("wifi.status", "accepted");
    persist();
  }

  void wifiActual(int result) throws Exception {
    CameraIdentity id = live();
    if (!pending()
        || !wifiRechecked
        || id == null
        || !id.model.equals(state.getProperty("wifi.model"))
        || !id.firmware.equals(state.getProperty("wifi.firmware")))
      throw new IOException("先重新查询相机，不能按本地记录确认完成");
    if (result < 0 || result > 2) throw new IOException("显示结果无效");
    if (result == 0) return;
    state.setProperty("wifi.pending", "false");
    state.setProperty("wifi.display", result == 1 ? "correct" : "incorrect");
    state.setProperty("wifi.status", "display_recorded");
    persist();
  }

  DesktopCard card(Path root, boolean write) throws Exception {
    if (fixtureRoot != null) {
      Path allowed = fixtureRoot.resolve("card").toAbsolutePath().normalize();
      if (!root.toAbsolutePath().normalize().equals(allowed))
        throw new IOException("模拟进程只能操作它的测试卡目录");
      Files.createDirectories(allowed);
      return new DesktopCard(allowed, write, true);
    }
    return new DesktopCard(root, write, false);
  }

  DesktopCard readCopy(Path root) throws Exception {
    return new DesktopCard(root, false, true, "读回副本");
  }

  Gr3Profile profile() {
    CameraIdentity c = context();
    if (c != null && c.model.contains("Urban")) return Gr3Profile.URBAN;
    if (c != null && c.model.endsWith("HDF")) return Gr3Profile.HDF;
    return Gr3Profile.STANDARD;
  }

  void newSd(Gr3Profile profile, boolean factory) throws Exception {
    CameraIdentity c = context();
    if (c == null || !c.sdSupported()) throw new IOException("先识别适用的 GR IIIx 1.70 相机");
    if (!c.model.equals("RICOH GR IIIx") && profile != profile())
      throw new IOException("机身版本与实际识别不符");
    byte[] stock = resources.stock(profile), candidate;
    if (factory) candidate = stock;
    else {
      if (prepared == null) throw new IOException("先选择图片");
      BufferedImage bitmap = images.decode(prepared);
      int[] pixels = bitmap.getRGB(0, 0, 720, 480, null, 0, 720);
      candidate =
          new Gr3JpegEncoder(pixels, stock, System.nanoTime() + 30_000_000_000L)
              .exact(profile.bytes)
              .jpeg;
    }
    job =
        new Gr3SdSession(
            profile,
            UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT),
            stock,
            candidate,
            factory,
            "desktop");
    state.setProperty("sd.model", c.model);
    state.setProperty("sd.firmware", c.firmware);
    state.remove("sd.card");
    state.remove("sd.cleaned");
    sdVerified = false;
    sdResult = null;
    saveJob();
    persist();
  }

  void saveJob() throws Exception {
    byte[] bytes = job.save();
    store.write("sd-" + job.id + ".gr3sd", bytes);
    store.write("image-task.gr3sd", bytes);
    state.setProperty("sd.active", job.id);
    persist();
  }

  void importSd(byte[] archive) throws Exception {
    Gr3SdSession next = Gr3SdSession.load(archive, resources::stock);
    CameraIdentity c = context();
    if (c == null
        || !c.sdSupported()
        || !c.model.equals("RICOH GR IIIx") && next.profile != profile())
      throw new IOException("任务与本次识别机型不符");
    job = next;
    state.setProperty("sd.model", c.model);
    state.setProperty("sd.firmware", c.firmware);
    state.remove("sd.card");
    sdVerified = false;
    sdResult = null;
    saveJob();
    persist();
  }

  void sdTarget() throws Exception {
    if (job == null) throw new IOException("先准备本地任务");
    CameraIdentity expected =
        new CameraIdentity(state.getProperty("sd.model", ""), state.getProperty("sd.firmware", ""));
    same(expected);
    if (!expected.sdSupported()) throw new IOException("此机型或固件不适用 SD 换图");
  }

  void reconcile(DesktopCard card, boolean copy) throws Exception {
    sdTarget();
    sdVerified = false;
    String binding = state.getProperty("sd.card", "");
    if (!copy && !binding.isEmpty() && !binding.equals(card.key()))
      throw new IOException("不是本任务原来的卡片");
    SdReconciler.Result result = SdReconciler.inspect(card, job);
    if (!copy
        && !result.cleaned
        && (result.job.installed || result.job.restored)
        && !SdCardDeployment.exists(card, SdCardDeployment.RECORD))
      try {
        EnhancementBundle b = resources.bundle(context());
        EnhancementDeployment.Receipt receipt = EnhancementDeployment.receipt(card, b);
        if (receipt != null && EnhancementDeployment.active(card, b)) {
          EnhancementDeployment.verifyReceipt(card, receipt, false);
          result.cleaned = true;
          result.resumed = true;
        }
      } catch (Exception ignored) {
      }
    job = result.job;
    sdResult = result;
    sdCopy = copy;
    sdVerified = !result.interrupted;
    sdMessage = result.message;
    state.remove("sd.manual");
    if (!copy && result.cleaned && (job.installed || job.restored)) {
      state.setProperty("sd.cleaned", job.id);
      if (result.resumed) state.setProperty("sd.enhanced", job.id);
    } else {
      state.remove("sd.cleaned");
      state.remove("sd.script_checked");
      state.remove("sd.enhanced");
    }
    saveJob();
  }

  record SdReview(
      SdCardDeployment.Plan plan,
      SdCardDeployment.Inspection inspection,
      EnhancementBundle enhancer,
      boolean pause) {}

  SdReview inspectSd(DesktopCard card, int phase) throws Exception {
    sdTarget();
    if (!sdVerified || sdResult == null || sdResult.interrupted)
      throw new IOException("先读取当前卡片核实进度");
    if (!state.getProperty("sd.card", card.key()).equals(card.key()))
      throw new IOException("目标卡不一致");
    SdReconciler.Result latest = SdReconciler.inspect(card, job);
    if (latest.interrupted) throw new IOException(latest.message);
    if (latest.preparedPhase == phase) throw new IOException("本阶段文件已准备，请在相机执行后读回，不重复写卡");
    job = latest.job;
    SdCardDeployment.Plan plan = new SdCardDeployment.Plan(job, phase);
    EnhancementBundle b = resources.bundle(context());
    boolean active = EnhancementDeployment.active(card, b);
    if (active || EnhancementDeployment.paused(card, b)) {
      EnhancementDeployment.Receipt r = EnhancementDeployment.receipt(card, b);
      if (r == null) throw new IOException("先在拍摄增强中核验接管现有入口");
      plan.files.put(SdCardDeployment.ENTRY, b.all.get(SdCardDeployment.ENTRY));
    }
    SdCardDeployment.Card view = active ? EnhancementDeployment.withoutStartup(card) : card;
    return new SdReview(plan, SdCardDeployment.inspect(view, plan), b, active);
  }

  void deploySd(DesktopCard card, SdReview review) throws Exception {
    sdTarget();
    if (review.pause()) EnhancementDeployment.suspend(card, review.enhancer(), () -> sdTarget());
    SdCardDeployment.deploy(card, review.plan(), review.inspection(), () -> sdTarget());
    state.setProperty("sd.card", card.key());
    state.remove("sd.cleaned");
    persist();
    reconcile(card, false);
  }

  void sdActual(int result) throws Exception {
    sdTarget();
    if (!sdVerified || job == null || !job.installed && !job.restored)
      throw new IOException("先核实本轮完整目标读回");
    if (result < 0 || result > 2) throw new IOException("显示结果无效");
    if (result == 0) return;
    job.display = result;
    saveJob();
  }

  void cleanup(DesktopCard card) throws Exception {
    sdTarget();
    if (!state.getProperty("sd.card", card.key()).equals(card.key()))
      throw new IOException("目标卡不一致");
    int display = job.display;
    SdCardDeployment.Inspection i = SdCardDeployment.inspectCleanup(card, job);
    SdCardDeployment.cleanup(card, job, i, () -> sdTarget());
    EnhancementBundle b = resources.bundle(context());
    boolean paused = EnhancementDeployment.paused(card, b);
    if (paused) {
      EnhancementDeployment.resume(card, b, () -> sdTarget());
      byte[] boot = optional(card, "GRKIT/BOOT.TXT");
      recordEnh(context(), card.key(), selectionFromCard(card), boot, false);
      state.setProperty("sd.enhanced", job.id);
    }
    state.setProperty("sd.cleaned", job.id);
    persist();
    reconcile(card, false);
    job.display = display;
    saveJob();
  }

  boolean selected(DesktopCard card, String module) throws Exception {
    return EnhancementBundle.list(card.read(EnhancementBundle.LIST, 8192)).contains(module);
  }

  byte[] optional(SdCardDeployment.Card card, String path) throws Exception {
    return SdCardDeployment.exists(card, path) ? card.read(path, 512 * 1024) : new byte[0];
  }

  byte[] sdPackage(int phase) throws Exception {
    sdTarget();
    if (!sdVerified) throw new IOException("先读卡核实进度");
    return job.pack(phase);
  }

  void manualSdSaved(int phase) throws Exception {
    state.setProperty("sd.manual", job.id + "/" + phase);
    state.remove("sd.cleaned");
    state.remove("sd.script_checked");
    state.remove("sd.enhanced");
    sdVerified = false;
    persist();
  }

  boolean hasEnh() {
    return !state.getProperty("enh.id", "").isEmpty();
  }

  CameraIdentity enhIdentity() {
    return new CameraIdentity(
        state.getProperty("enh.model", ""), state.getProperty("enh.firmware", ""));
  }

  void recordEnh(CameraIdentity id, String card, boolean f, boolean g, byte[] boot, boolean manual)
      throws Exception {
    recordEnh(id, card, EnhancementSelection.basic(f, g), boot, manual);
  }

  EnhancementSelection enhSelection() throws Exception {
    String saved = state.getProperty("enh.effects", "");
    return new EnhancementSelection(
        Boolean.parseBoolean(state.getProperty("enh.f")),
        Boolean.parseBoolean(state.getProperty("enh.g")),
        Boolean.parseBoolean(state.getProperty("enh.film", "false")),
        Boolean.parseBoolean(state.getProperty("enh.date", "false")),
        saved.isEmpty() ? List.of() : Arrays.asList(saved.split(",")));
  }

  EnhancementSelection selectionFromCard(DesktopCard card) throws Exception {
    Set<String> names = EnhancementBundle.list(optional(card, EnhancementBundle.LIST));
    return new EnhancementSelection(
        names.contains("FRAMING.GRM"),
        names.contains("GUIDES.GRM"),
        names.contains("FILMLOOK.GRM"),
        names.contains("DATE.GRM"),
        names.stream().filter(n -> n.endsWith(".GRF")).toList());
  }

  void recordEnh(
      CameraIdentity id, String card, EnhancementSelection choice, byte[] boot, boolean manual)
      throws Exception {
    state.setProperty("enh.id", UUID.randomUUID().toString());
    state.setProperty("enh.model", id.model);
    state.setProperty("enh.firmware", id.firmware);
    state.setProperty("enh.card", card);
    state.setProperty("enh.f", String.valueOf(choice.framing));
    state.setProperty("enh.g", String.valueOf(choice.guides));
    state.setProperty("enh.film", String.valueOf(choice.film));
    state.setProperty("enh.date", String.valueOf(choice.date));
    state.setProperty("enh.effects", String.join(",", choice.effects));
    state.setProperty("enh.manual", String.valueOf(manual));
    state.setProperty("enh.baseline_len", String.valueOf(boot.length));
    state.setProperty("enh.baseline_hash", EndScreenPolicy.sha256(boot));
    state.setProperty("enh.actual_f", "0");
    state.setProperty("enh.actual_g", "0");
    state.setProperty("enh.actual_film", "0");
    state.setProperty("enh.actual_date", "0");
    enhCardChecked = true;
    enhReportChecked = false;
    persist();
  }

  void prepareEnh(
      DesktopCard card,
      CameraIdentity id,
      boolean f,
      boolean g,
      EnhancementDeployment.Inspection reviewed)
      throws Exception {
    prepareEnh(card, id, EnhancementSelection.basic(f, g), reviewed);
  }

  void prepareEnh(
      DesktopCard card,
      CameraIdentity id,
      EnhancementSelection choice,
      EnhancementDeployment.Inspection reviewed)
      throws Exception {
    same(id);
    byte[] boot = optional(card, "GRKIT/BOOT.TXT");
    EnhancementBundle b = resources.bundle(id);
    EnhancementDeployment.apply(card, b, choice, reviewed, () -> same(id));
    if (reviewed.remove) {
      state.remove("enh.id");
      enhCardChecked = enhReportChecked = false;
      persist();
    } else recordEnh(id, card.key(), choice, boot, false);
  }

  void readEnh(DesktopCard card, boolean copy) throws Exception {
    if (!hasEnh()) throw new IOException("尚无本次增强任务");
    same(enhIdentity());
    enhCardChecked = enhReportChecked = false;
    state.setProperty("enh.actual_f", "0");
    state.setProperty("enh.actual_g", "0");
    state.setProperty("enh.actual_film", "0");
    state.setProperty("enh.actual_date", "0");
    persist();
    boolean manual = Boolean.parseBoolean(state.getProperty("enh.manual"));
    if (!manual) {
      if (copy) throw new IOException("本任务直接部署过卡片，续办请核实实际卡片，不能只使用复制报告");
      if (!card.key().equals(state.getProperty("enh.card"))) throw new IOException("不是原增强任务的卡片");
      EnhancementBundle b = resources.bundle(enhIdentity());
      EnhancementDeployment.Receipt r = EnhancementDeployment.receipt(card, b);
      if (r == null) throw new IOException("卡内缺少本工具部署记录");
      EnhancementDeployment.verifyReceipt(card, r, false);
    }
    enhCardChecked = true;
    if (SdCardDeployment.exists(card, "SCRIPT/GRKIT/INSTALL.ERR")
        && card.read("SCRIPT/GRKIT/INSTALL.ERR", 32768).length > 0)
      throw new IOException("相机存在安装错误日志，请检查后再读回");
    String
        report =
            SdCardDeployment.exists(card, "GRKIT/REPORT.TXT") ? "GRKIT/REPORT.TXT" : "REPORT.TXT",
        boot = SdCardDeployment.exists(card, "GRKIT/BOOT.TXT") ? "GRKIT/BOOT.TXT" : "BOOT.TXT";
    if (!SdCardDeployment.exists(card, report))
      throw new IOException("尚未找到运行报告。请把卡放回相机加载 GRKit，再将卡接回电脑读取。");
    if (!SdCardDeployment.exists(card, boot))
      throw new IOException("尚未找到启动日志。请在相机完成加载后，再读取这张 SD 卡。");
    EnhancementReport.verify(
        enhIdentity(),
        enhSelection(),
        card.read(report, 32768),
        card.read(boot, 512 * 1024),
        Integer.parseInt(state.getProperty("enh.baseline_len", "-1")),
        state.getProperty("enh.baseline_hash", ""));
    enhReportChecked = true;
    enhMessage = "文件与本轮新增运行报告已核实；接下来核对相机实际效果。";
  }

  void enhActual(boolean framing, int result) throws Exception {
    enhActual(framing ? "f" : "g", result);
  }

  void enhActual(String module, int result) throws Exception {
    if (!enhReportChecked) throw new IOException("先读取本轮真实报告");
    if (!List.of("f", "g", "film", "date").contains(module) || (result != 1 && result != 2))
      throw new IOException("效果核对选项无效");
    state.setProperty("enh.actual_" + module, String.valueOf(result));
    persist();
  }
}
