package local.grworld.studio.spi;

import java.util.List;

/** Enumerates eligible volumes; an id is valid only while that same volume remains mounted. */
public interface CardProvider {
  record Volume(String id, String label) {
    public String toString() {
      return label;
    }
  }

  List<Volume> volumes() throws Exception;

  CardAccess open(String id, boolean writable) throws Exception;
}
