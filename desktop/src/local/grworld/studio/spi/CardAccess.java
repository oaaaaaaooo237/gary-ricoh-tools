package local.grworld.studio.spi;

import java.util.List;

/** Version1 volume-bound file API. Paths are relative and must reject links/escapes. */
public interface CardAccess {
  String key();

  void check() throws Exception;

  List<String> list(String directory) throws Exception;

  boolean directory(String path) throws Exception;

  byte[] read(String path, int limit) throws Exception;

  void mkdir(String path) throws Exception;

  void create(String path, byte[] bytes) throws Exception;

  void delete(String path) throws Exception;

  void rename(String path, String newName) throws Exception;
}
