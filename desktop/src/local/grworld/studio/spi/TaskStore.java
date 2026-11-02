package local.grworld.studio.spi;

import java.util.List;

/** Version1 durable storage. Implementations replace complete files atomically. */
public interface TaskStore {
  byte[] read(String key) throws Exception;

  void write(String key, byte[] data) throws Exception;

  boolean exists(String key);

  List<String> list(String prefix) throws Exception;
}
