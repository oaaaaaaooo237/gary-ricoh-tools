package local.grworld.studio.spi;

/** Version1 transport contract; implementations must never retry an uncertain upload. */
public interface CameraTransport extends AutoCloseable {
  record Identity(String model, String firmware, String networkKey, long elapsedMillis) {}

  Identity identify() throws Exception;

  void upload(byte[] jpeg, String expectedNetworkKey) throws Exception;

  default boolean simulated() {
    return false;
  }

  default void close() throws Exception {}
}
