package local.grworld.studio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Wi-Fi upload transport with a fixed, read-only USB identity fallback for SD tasks. */
final class DesktopUsbCamera extends DesktopCamera {
  private volatile boolean wifi;

  @Override
  public Identity identify() throws Exception {
    wifi = false;
    try {
      Identity result = super.identify();
      wifi = true;
      return result;
    } catch (Exception unavailable) {
      Path jar = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
      Path helper = jar.resolveSibling("native").resolve("UsbIdentity.exe");
      if (!Files.isRegularFile(helper))
        throw new IOException("未连接相机 Wi-Fi，且 USB 识别组件缺失", unavailable);
      long started = System.nanoTime();
      Process process = new ProcessBuilder(helper.toString()).redirectErrorStream(true).start();
      try {
        if (!process.waitFor(4, TimeUnit.SECONDS)) throw new IOException("USB 只读识别超时，请检查连接");
        byte[] bytes = DesktopResources.limited(process.getInputStream(), 8192);
        JSONObject report = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (process.exitValue() != 0 || report.has("Error"))
          throw new IOException("请连接相机 Wi-Fi，或使用 USB 连接 GR IV HDF 1.20");
        if (!"0x1001".equals(report.optString("ReadOpcode"))
            || report.optInt("CameraWriteRequests", -1) != 0
            || !"RICOH GR IV HDF".equals(report.optString("Model"))
            || !"1.20".equals(report.optString("Firmware")))
          throw new IOException("USB 身份结果不符合当前支持范围");
        return new Identity(
            report.getString("Model"),
            report.getString("Firmware"),
            "usb:read-only",
            (System.nanoTime() - started) / 1_000_000);
      } finally {
        if (process.isAlive()) process.destroyForcibly();
      }
    }
  }

  @Override
  public boolean canUpload() {
    return wifi;
  }

  @Override
  public void upload(byte[] jpeg, String expectedNetworkKey) throws Exception {
    if (!wifi || expectedNetworkKey.startsWith("usb:"))
      throw new IOException("USB 仅用于只读识别；关机图上传需要相机 Wi-Fi");
    super.upload(jpeg, expectedNetworkKey);
  }
}
