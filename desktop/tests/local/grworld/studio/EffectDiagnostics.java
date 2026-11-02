package local.grworld.studio;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Backend checks, not GUI or physical-camera acceptance. */
public final class EffectDiagnostics {
  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  interface Action {
    void run() throws Exception;
  }

  static void rejected(Action action) throws Exception {
    try {
      action.run();
    } catch (Exception expected) {
      return;
    }
    throw new AssertionError("Expected rejection");
  }

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("gary-effects-diagnostics-");
    for (CameraIdentity identity :
        List.of(
            new CameraIdentity("RICOH GR IV", "1.20"),
            new CameraIdentity("RICOH GR IV HDF", "1.20"),
            new CameraIdentity("RICOH GR III", "2.20"),
            new CameraIdentity("RICOH GR IIIx", "1.70"))) {
      DesktopStore store = new DesktopStore(root.resolve(UUID.randomUUID().toString()));
      DesktopResources resources = new DesktopResources();
      DesktopEffects effects = new DesktopEffects(store);
      resources.effects = effects;
      EnhancementBundle bundle = resources.bundle(identity);
      List<DesktopEffects.Effect> catalog = effects.list(identity, bundle);
      require(catalog.size() >= 16, "Bundled effect catalog");
      String first = catalog.get(0).file;
      EnhancementSelection choice =
          new EnhancementSelection(true, true, true, true, List.of(first));
      var files = bundle.files(choice, null);
      require(
          files.containsKey("SCRIPT/GRKIT/MODULES/FILMLOOK.GRM")
              && files.containsKey("SCRIPT/GRKIT/MODULES/DATE.GRM"),
          "New modules included");
      require(
          files.keySet().stream().filter(p -> p.startsWith("SCRIPT/GRKIT/EFFECTS/")).count() == 1,
          "Only selected effects included");
      byte[] corrupt = catalog.get(0).blob.clone();
      corrupt[150] ^= 1;
      rejected(() -> DesktopEffects.decode(corrupt));
      String rt = EnhancementPolicy.family(identity).equals("gr4") ? "gr4-1.20" : "gr3-2.10";
      byte[] report =
          ("GRKit 1.3.5 ("
                  + rt
                  + ")\n"
                  + "FRAMING.GRM  loaded\n"
                  + "GUIDES.GRM  loaded\n"
                  + "FILMLOOK.GRM  loaded\n"
                  + "DATE.GRM  loaded\n"
                  + "Test ("
                  + first
                  + ")  loaded\n")
              .getBytes(StandardCharsets.UTF_8);
      byte[] boot = "7 install 0\n".getBytes(StandardCharsets.US_ASCII);
      EnhancementReport.verify(
          identity, choice, report, boot, 0, EndScreenPolicy.sha256(new byte[0]));
      byte[] missing =
          new String(report, StandardCharsets.UTF_8)
              .replace("DATE.GRM  loaded\n", "")
              .getBytes(StandardCharsets.UTF_8);
      rejected(
          () ->
              EnhancementReport.verify(
                  identity, choice, missing, boot, 0, EndScreenPolicy.sha256(new byte[0])));
      Path cardRoot = root.resolve(UUID.randomUUID().toString());
      Files.createDirectories(cardRoot);
      DesktopCard card = new DesktopCard(cardRoot, true, true);
      var inspected = EnhancementDeployment.inspect(card, bundle, choice, false);
      rejected(
          () ->
              EnhancementDeployment.apply(
                  card,
                  bundle,
                  new EnhancementSelection(true, true, true, false, List.of(first)),
                  inspected,
                  () -> {}));
      EnhancementDeployment.apply(card, bundle, choice, inspected, () -> {});
      EnhancementDeployment.verifyReceipt(card, EnhancementDeployment.receipt(card, bundle), false);
      var removal = EnhancementDeployment.inspect(card, bundle, choice, true);
      EnhancementDeployment.apply(card, bundle, choice, removal, () -> {});
      require(!SdCardDeployment.exists(card, EnhancementBundle.START), "Owned startup removed");
    }
    DesktopStore store = new DesktopStore(root.resolve("import"));
    Path cube = root.resolve("warm.cube");
    Files.writeString(
        cube,
        "LUT_3D_SIZE 2\n0 0 0\n1 0 0\n0 0.9 0\n1 0.9 0\n0 0 0.8\n1 0 0.8\n0 0.9 0.8\n1 0.9 0.8\n");
    CameraIdentity identity = new CameraIdentity("RICOH GR IV HDF", "1.20");
    DesktopEffects.Effect imported =
        DesktopLutConverter.convert(store, identity, cube, "Warm Test", "srgb");
    require(
        imported.preview.length == DesktopEffects.TABLE_BYTES && imported.grade.matches("[ABCD]"),
        "Real WASM fit returns data and grade");
    new DesktopEffects(store).save(identity, imported);
    DesktopResources resources = new DesktopResources();
    resources.effects = new DesktopEffects(new DesktopStore(store.root));
    require(
        resources.bundle(identity).all.containsKey("SCRIPT/GRKIT/EFFECTS/" + imported.file),
        "Imported effect survives library reopen");
    byte[] hald = DesktopLutConverter.hald(store);
    require(
        hald.length > 8 && hald[1] == 'P' && hald[2] == 'N' && hald[3] == 'G',
        "Hald template export");
    Files.writeString(cube, "not a LUT");
    rejected(() -> DesktopLutConverter.convert(store, identity, cube, "Broken", "auto"));
    System.out.println(
        "Effect diagnostics passed; GUI and real-camera acceptance remain separate.");
  }
}
