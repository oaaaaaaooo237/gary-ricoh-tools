package local.grworld.studio;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.*;
import local.grworld.studio.spi.ImageProcessor;

final class DesktopImages implements ImageProcessor {
  public BufferedImage decode(byte[] data) throws Exception {
    if (data.length > 40 * 1024 * 1024) throw new IOException("图片超过40MB");
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException("请选择 JPEG、PNG 或系统支持的图片");
      ImageReader r = readers.next();
      try {
        r.setInput(input);
        int w = r.getWidth(0), h = r.getHeight(0);
        if (w <= 0 || h <= 0 || (long) w * h > 100_000_000) throw new IOException("图片像素过大");
        int sample = Math.max(1, (int) Math.ceil(Math.max(w, h) / 6000.0));
        ImageReadParam p = r.getDefaultReadParam();
        p.setSourceSubsampling(sample, sample, 0, 0);
        return orient(r.read(0, p), orientation(data));
      } finally {
        r.dispose();
      }
    }
  }

  static int orientation(byte[] bytes) {
    try {
      if (bytes.length < 4 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216) return 1;
      int p = 2;
      while (p + 4 < bytes.length && (bytes[p] & 255) == 255) {
        int marker = bytes[p + 1] & 255;
        if (marker == 0xda || marker == 0xd9) break;
        int len = ((bytes[p + 2] & 255) << 8) | (bytes[p + 3] & 255);
        if (len < 2 || p + len + 2 > bytes.length) break;
        if (marker == 0xe1
            && len > 16
            && bytes[p + 4] == 'E'
            && bytes[p + 5] == 'x'
            && bytes[p + 6] == 'i'
            && bytes[p + 7] == 'f') {
          int base = p + 10, end = p + 2 + len;
          java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(bytes);
          b.order(
              bytes[base] == 'I' && bytes[base + 1] == 'I'
                  ? java.nio.ByteOrder.LITTLE_ENDIAN
                  : java.nio.ByteOrder.BIG_ENDIAN);
          if (b.getShort(base + 2) != 42) return 1;
          int off = b.getInt(base + 4);
          if (off < 8 || off > end - base - 2) return 1;
          int at = base + off, count = b.getShort(at) & 65535;
          for (int i = 0; i < count && i < 512; i++) {
            int e = at + 2 + i * 12;
            if (e + 12 > end) break;
            if ((b.getShort(e) & 65535) == 274 && b.getShort(e + 2) == 3 && b.getInt(e + 4) == 1) {
              int value = b.getShort(e + 8) & 65535;
              return value >= 1 && value <= 8 ? value : 1;
            }
          }
        }
        p += len + 2;
      }
    } catch (RuntimeException ignored) {
    }
    return 1;
  }

  static BufferedImage orient(BufferedImage source, int value) {
    if (value == 1) return source;
    int w = source.getWidth(), h = source.getHeight();
    BufferedImage out =
        new BufferedImage(value >= 5 ? h : w, value >= 5 ? w : h, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        int dx = x, dy = y;
        switch (value) {
          case 2 -> dx = w - 1 - x;
          case 3 -> {
            dx = w - 1 - x;
            dy = h - 1 - y;
          }
          case 4 -> dy = h - 1 - y;
          case 5 -> {
            dx = y;
            dy = x;
          }
          case 6 -> {
            dx = h - 1 - y;
            dy = x;
          }
          case 7 -> {
            dx = h - 1 - y;
            dy = w - 1 - x;
          }
          case 8 -> {
            dx = y;
            dy = w - 1 - x;
          }
        }
        out.setRGB(dx, dy, source.getRGB(x, y));
      }
    return out;
  }

  public byte[] prepare(BufferedImage source, Edit edit) throws Exception {
    BufferedImage target = new BufferedImage(720, 480, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = target.createGraphics();
    try {
      g.setColor(new Color(edit.background()));
      g.fillRect(0, 0, 720, 480);
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      double sx = 720.0 / source.getWidth(), sy = 480.0 / source.getHeight();
      double scale =
          edit.mode().equals("crop")
              ? Math.max(sx, sy)
              : edit.mode().equals("original") ? Math.min(1, Math.min(sx, sy)) : Math.min(sx, sy);
      int w, h;
      if (edit.mode().equals("stretch")) {
        w = 720;
        h = 480;
      } else {
        w = (int) Math.round(source.getWidth() * scale * edit.zoom());
        h = (int) Math.round(source.getHeight() * scale * edit.zoom());
      }
      int x = (720 - w) / 2 + (int) (edit.panX() * Math.abs(w - 720) / 2),
          y = (480 - h) / 2 + (int) (edit.panY() * Math.abs(h - 480) / 2);
      g.drawImage(source, x, y, w, h, null);
    } finally {
      g.dispose();
    }
    return jpeg(target, edit.quality());
  }

  static byte[] jpeg(BufferedImage image, int quality) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(out);
      ImageWriteParam p = writer.getDefaultWriteParam();
      p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      p.setCompressionQuality(Math.max(.3f, Math.min(1f, quality / 100f)));
      p.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
      writer.write(null, new IIOImage(image, null, null), p);
    } finally {
      writer.dispose();
    }
    byte[] result = bytes.toByteArray();
    EndScreenPolicy.image(result);
    return result;
  }

  static javafx.scene.image.Image fx(BufferedImage source) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(source, "png", out);
    return new javafx.scene.image.Image(new ByteArrayInputStream(out.toByteArray()));
  }
}
