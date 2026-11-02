package local.grworld.studio;

import java.io.*;
import java.util.*;

final class DesktopResources {
  byte[] read(String name) throws IOException {
    if (!name.matches("[A-Za-z0-9_.-]+")) throw new IOException("资源名称无效");
    try (InputStream in = getClass().getResourceAsStream("/assets/" + name)) {
      if (in == null) throw new IOException("缺少资源：" + name);
      return limited(in, 4 * 1024 * 1024);
    }
  }

  static byte[] limited(InputStream in, int max) throws IOException {
    try (in;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        if (out.size() + n > max) throw new IOException("内容超过限制");
        out.write(b, 0, n);
      }
      return out.toByteArray();
    }
  }

  byte[] stock(CameraIdentity id) throws Exception {
    EndScreenPolicy.camera(id.model, id.firmware);
    byte[] bytes = read(EnhancementPolicy.stockAsset(id));
    EndScreenPolicy.image(bytes);
    if (!EnhancementPolicy.stockHash(id).equals(EndScreenPolicy.sha256(bytes)))
      throw new IOException("原厂资源校验失败");
    return bytes;
  }

  byte[] stock(Gr3Profile p) throws Exception {
    byte[] b = read(p.asset);
    p.stock(b);
    return b;
  }

  EnhancementBundle bundle(CameraIdentity id) throws Exception {
    String family = EnhancementPolicy.family(id);
    if (family == null) throw new IOException("当前机型或固件不支持拍摄增强");
    return new EnhancementBundle(family, read("grkit-" + family + ".zip"));
  }
}
