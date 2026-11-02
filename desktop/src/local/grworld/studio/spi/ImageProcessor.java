package local.grworld.studio.spi;

import java.awt.image.BufferedImage;

/** Version1 image contract. The preview must decode the exact returned JPEG bytes. */
public interface ImageProcessor {
  record Edit(String mode, int background, double zoom, double panX, double panY, int quality) {}

  BufferedImage decode(byte[] source) throws Exception;

  byte[] prepare(BufferedImage source, Edit edit) throws Exception;
}
