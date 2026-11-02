package local.grworld.studio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import local.grworld.studio.spi.CameraTransport;

/** Explicit UI-test process only; no network requests and no real camera success claim. */
final class FixtureCamera implements CameraTransport {
  final Path folder;

  FixtureCamera(Path folder) throws IOException {
    this.folder = folder;
    Files.createDirectories(folder);
    Path config = folder.resolve("camera.properties");
    if (!Files.exists(config))
      Files.writeString(config, "model=RICOH GR IV HDF\nfirmware=1.20\nconnected=true\n");
  }

  public Identity identify() throws Exception {
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(folder.resolve("camera.properties"))) {
      p.load(in);
    }
    if (!Boolean.parseBoolean(p.getProperty("connected", "true"))) throw new IOException("模拟相机已断开");
    return new Identity(p.getProperty("model"), p.getProperty("firmware"), "fixture", 1);
  }

  public void upload(byte[] jpeg, String expected) throws Exception {
    if (!expected.equals("fixture")) throw new IOException("模拟会话已变化");
    identify();
    EndScreenPolicy.image(jpeg);
    Files.write(folder.resolve("uploaded.jpg"), jpeg);
  }

  public boolean simulated() {
    return true;
  }
}
