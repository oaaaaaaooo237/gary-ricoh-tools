package local.grworld.studio;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import local.grworld.studio.spi.*;
import org.json.JSONObject;

/** Developer diagnostics with synthetic devices/cards. Not visual or camera acceptance. */
public final class DesktopDiagnostics {
  interface Checked {
    void run() throws Exception;
  }

  static void require(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }

  static void rejected(Checked action) throws Exception {
    try {
      action.run();
    } catch (Exception expected) {
      return;
    }
    throw new AssertionError("Expected rejection");
  }

  static final class Camera implements CameraTransport {
    String model = "RICOH GR IV HDF", firmware = "1.20", network = "test";
    boolean connected = true, failUpload, changeNetwork;
    int reads, writes;

    public Identity identify() throws Exception {
      if (!connected) throw new IOException("Disconnected");
      reads++;
      return new Identity(
          model, firmware, changeNetwork && reads % 2 == 0 ? "changed" : network, 1);
    }

    public void upload(byte[] image, String key) throws Exception {
      writes++;
      if (failUpload) throw new IOException("Unknown outcome");
    }

    public boolean simulated() {
      return true;
    }
  }

  static DesktopSession session(Path root, Camera c) throws Exception {
    return new DesktopSession(c, new DesktopStore(root.resolve("tasks")), root.resolve("fixture"));
  }

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("gary-desktop-diagnostics-");
    images(root.resolve("images"));
    wifi(root.resolve("wifi"));
    sd(root.resolve("sd"));
    manualSd(root.resolve("manual-sd"));
    card(root.resolve("card"));
    reports();
    System.out.println(
        "Backend diagnostics passed. Visual/manual/camera acceptance remains separate.");
  }

  static void images(Path root) throws Exception {
    DesktopSession s = session(root, new Camera());
    BufferedImage source = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = source.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 60, 80);
    g.setColor(Color.BLUE);
    g.fillRect(60, 0, 60, 80);
    g.dispose();
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(source, "png", png);
    s.importImage(png.toByteArray(), "test", false);
    for (String mode : java.util.List.of("fit", "crop", "stretch", "original")) {
      byte[] result =
          s.images.prepare(source, new ImageProcessor.Edit(mode, 0x808080, 1, 0, 0, 92));
      EndScreenPolicy.image(result);
      BufferedImage image = s.images.decode(result);
      require(image.getWidth() == 720 && image.getHeight() == 480, "JPEG dimensions");
      if (mode.equals("original"))
        require(Math.abs((image.getRGB(0, 0) & 255) - 128) < 5, "Gray matte");
    }
    BufferedImage rotated = DesktopImages.orient(source, 6);
    require(rotated.getWidth() == 80 && rotated.getHeight() == 120, "EXIF rotation dimensions");
    require(rotated.getRGB(0, 0) == source.getRGB(0, 79), "EXIF clockwise rotation");
    s.process(new ImageProcessor.Edit("crop", 0x808080, 1.5, .3, -.4, 88));
    byte[] exact = s.prepared.clone(), project = s.project();
    DesktopSession reopened = session(root, new Camera());
    require(
        Arrays.equals(reopened.prepared, exact)
            && reopened.edit.mode().equals("crop")
            && reopened.edit.zoom() == 1.5,
        "Restart retains image/edit");
    reopened.openProject(project);
    require(Arrays.equals(reopened.prepared, exact), "Project retains exact JPEG");
    Map<String, byte[]> entries = Gr3SdSession.unzip(project);
    JSONObject metadata =
        new JSONObject(new String(entries.get("project.json"), StandardCharsets.UTF_8));
    metadata.put("mode", 99);
    entries.put("project.json", metadata.toString().getBytes(StandardCharsets.UTF_8));
    rejected(() -> reopened.openProject(Gr3SdSession.zip(entries)));
    require(
        Arrays.equals(reopened.prepared, exact), "Invalid project must not change active image");
  }

  static void wifi(Path root) throws Exception {
    Camera camera = new Camera();
    DesktopSession s = session(root, camera);
    byte[] image = s.resources.stock(new CameraIdentity(camera.model, camera.firmware));
    s.observe();
    s.importImage(image, "test", true);
    s.send(image, false);
    require(camera.writes == 1 && s.pending(), "Upload creates pending task");
    rejected(() -> s.send(image, false));
    require(camera.writes == 1, "No duplicate upload");
    camera.connected = false;
    rejected(s::observe);
    require(!s.wifiRechecked, "Disconnect invalidates confirmation");
    rejected(() -> s.wifiActual(1));
    camera.connected = true;
    camera.model = "RICOH GR IV";
    s.observe();
    rejected(() -> s.wifiActual(1));
    camera.model = "RICOH GR IV HDF";
    s.observe();
    s.checkedAt = 0;
    rejected(() -> s.wifiActual(1));
    s.observe();
    s.wifiActual(1);
    require(!s.pending(), "Human result after fresh identity");
    camera.failUpload = true;
    s.observe();
    rejected(() -> s.send(image, false));
    require(
        s.pending() && s.state.getProperty("wifi.status").equals("outcome_unknown"),
        "Upload failure is unknown, not failed-safe retry");
    DesktopSession resume = session(root, camera);
    require(resume.pending() && !resume.wifiRechecked, "Restart requires external identity");
    rejected(() -> resume.wifiActual(1));
    require(camera.writes == 2, "Restart never retries upload");
    Camera switched = new Camera();
    DesktopSession t = session(root.resolve("network"), switched);
    t.observe();
    switched.changeNetwork = true;
    rejected(() -> t.send(image, false));
    require(switched.writes == 0 && !t.pending(), "Changing network blocks before upload");
    Camera gr3 = new Camera();
    gr3.model = "RICOH GR III";
    gr3.firmware = "2.20";
    DesktopSession u = session(root.resolve("gr3"), gr3);
    u.observe();
    require(
        !u.live().wifiSupported() && u.live().enhancementSupported(),
        "Capability gates independent");
    rejected(() -> u.send(image, false));
    require(gr3.writes == 0, "GR3 cannot enter Wi-Fi upload");
  }

  static void proof(DesktopCard c, Gr3SdSession j, int phase) throws Exception {
    for (String name : j.expected(phase)) {
      byte[] bytes =
          name.endsWith("TXT")
              ? j.marker(phase)
              : name.equals(j.name('O', "JPG"))
                      || name.equals(j.name('P', "JPG"))
                      || name.equals(j.name('R', "JPG"))
                  ? j.original
                  : j.candidate;
      c.create(name, bytes);
    }
  }

  static void sd(Path root) throws Exception {
    Camera c = new Camera();
    c.model = "RICOH GR IIIx";
    c.firmware = "1.70";
    DesktopSession s = session(root, c);
    s.observe();
    s.importImage(s.resources.stock(Gr3Profile.STANDARD), "test", true);
    s.newSd(Gr3Profile.STANDARD, false);
    String task = s.job.id;
    DesktopCard card = s.card(root.resolve("fixture/card"), true);
    rejected(() -> s.sdPackage(2));
    s.reconcile(card, false);
    require(!s.job.staged, "Empty card does not inherit progress");
    s.deploySd(card, s.inspectSd(card, 1));
    require(s.sdResult.preparedPhase == 1 && !s.job.staged, "Prepared is not executed");
    rejected(() -> s.inspectSd(card, 1));
    proof(card, s.job, 1);
    s.reconcile(card, false);
    require(s.job.staged && !s.job.installed, "Full stage proof advances only staging");
    s.deploySd(card, s.inspectSd(card, 2));
    proof(card, s.job, 2);
    s.reconcile(card, false);
    require(
        s.job.installed && s.job.display == 0, "Full target readback still needs display check");
    s.sdActual(1);
    s.cleanup(card);
    require(s.job.display == 1 && s.sdResult.cleaned, "Cleanup preserves display result");
    DesktopSession resume = session(root, c);
    require(
        resume.job != null && resume.job.id.equals(task) && !resume.sdVerified,
        "Restart restores task but not external verification");
    resume.observe();
    resume.reconcile(card, false);
    require(
        resume.job.installed && resume.job.display == 0,
        "Readback derives progress; actual display must be checked again");
    DesktopCard empty = s.card(root.resolve("fixture/card"), false);
    card.delete(s.job.name('I', "JPG"));
    rejected(() -> s.reconcile(empty, false));
    require(!s.sdVerified, "Partial external proof blocks cached success");
    DesktopSession partial = session(root.resolve("interrupted"), c);
    partial.observe();
    partial.newSd(Gr3Profile.STANDARD, true);
    DesktopCard p = partial.card(root.resolve("interrupted/fixture/card"), true);
    p.create(partial.job.name('A', "TXT"), "START".getBytes(StandardCharsets.US_ASCII));
    partial.reconcile(p, false);
    require(
        partial.sdResult.interrupted && !partial.sdVerified,
        "START without full proof blocks repetition");
    rejected(() -> partial.inspectSd(p, 1));
  }

  static void card(Path root) throws Exception {
    Files.createDirectories(root);
    DesktopCard c = new DesktopCard(root, true, true);
    c.create("Case.TXT", new byte[] {1, 2});
    require(c.read("case.txt", 2)[1] == 2, "Case-insensitive lookup");
    rejected(() -> c.read("../private", 10));
    DesktopCard read = new DesktopCard(root, false, true);
    require(c.key().equals(read.key()), "Read and write handles share card binding");
    rejected(() -> read.delete("Case.TXT"));
    rejected(() -> c.create("CASE.TXT", new byte[] {3}));
    rejected(() -> new DesktopCard(root, true, false));
  }

  static void manualSd(Path root) throws Exception {
    Camera camera = new Camera();
    camera.model = "RICOH GR IIIx";
    camera.firmware = "1.70";
    DesktopSession s = session(root, camera);
    s.observe();
    s.importImage(s.resources.stock(Gr3Profile.STANDARD), "test", true);
    s.newSd(Gr3Profile.STANDARD, false);
    DesktopCard card = s.card(root.resolve("fixture/card"), true);
    s.reconcile(card, false);
    s.manualSdSaved(1);
    require(!s.sdVerified, "Exported ZIP does not verify an SD card");
    card.mkdir("script");
    card.create(SdCardDeployment.ENTRY, Gr3SdSession.factoryEntry());
    card.create(SdCardDeployment.MOD, Gr3SdSession.developMode());
    card.create(SdCardDeployment.STARTUP, s.job.script(1));
    s.reconcile(card, false);
    require(
        s.sdResult.preparedPhase == 1 && !s.job.staged && s.state.getProperty("sd.manual") == null,
        "Exact manual files derive preparation, not execution");
    proof(card, s.job, 1);
    s.reconcile(card, false);
    card.delete(SdCardDeployment.STARTUP);
    card.create(SdCardDeployment.STARTUP, s.job.script(2));
    s.reconcile(card, false);
    require(
        s.sdResult.preparedPhase == 2 && !s.job.installed,
        "Manual install still needs target readback");
    proof(card, s.job, 2);
    s.reconcile(card, false);
    s.manualSdSaved(3);
    card.delete(SdCardDeployment.STARTUP);
    card.create(SdCardDeployment.STARTUP, s.job.script(3));
    s.reconcile(card, false);
    require(
        s.job.installed && !s.job.restored && s.sdResult.preparedPhase == 3,
        "Installed image and pending rollback are distinct states");
    rejected(() -> SdCardDeployment.inspectCleanup(card, s.job));
    proof(card, s.job, 3);
    s.reconcile(card, false);
    require(s.job.restored, "Manual restore requires complete matching backup readback");
    card.delete(SdCardDeployment.STARTUP);
    card.delete(SdCardDeployment.ENTRY);
    card.delete(SdCardDeployment.MOD);
    s.reconcile(card, false);
    require(
        s.sdResult.cleaned && s.job.id.equals(s.state.getProperty("sd.cleaned")),
        "External cleanup is derived from actual card state");
    s.state.setProperty("sd.script_checked", s.job.id);
    card.create(SdCardDeployment.ENTRY, Gr3SdSession.factoryEntry());
    card.create(SdCardDeployment.MOD, Gr3SdSession.developMode());
    card.create(SdCardDeployment.STARTUP, s.job.script(3));
    s.reconcile(card, false);
    require(
        !s.sdResult.cleaned
            && s.state.getProperty("sd.cleaned") == null
            && s.state.getProperty("sd.script_checked") == null,
        "Reappearing startup files invalidate cached cleanup and script closure");
  }

  static void reports() throws Exception {
    CameraIdentity id = new CameraIdentity("RICOH GR IV HDF", "1.20");
    byte[] report =
        "GRKit 1.3.5 (gr4-1.20)\nFRAMING  loaded\nGUIDES  loaded\n"
            .getBytes(StandardCharsets.UTF_8);
    byte[] baseline = "old install 0\n".getBytes(StandardCharsets.UTF_8);
    String hash = EndScreenPolicy.sha256(baseline);
    rejected(
        () -> EnhancementReport.verify(id, true, true, report, baseline, baseline.length, hash));
    byte[] good = "old install 0\nnew install 0\n".getBytes(StandardCharsets.UTF_8);
    EnhancementReport.verify(id, true, true, report, good, baseline.length, hash);
    byte[] failed =
        "old install 0\nnew install 0\nlast install -1\n".getBytes(StandardCharsets.UTF_8);
    rejected(() -> EnhancementReport.verify(id, true, true, report, failed, baseline.length, hash));
  }
}
