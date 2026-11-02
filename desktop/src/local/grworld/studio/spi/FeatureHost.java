package local.grworld.studio.spi;

public interface FeatureHost {
  CameraTransport camera();

  ImageProcessor images();

  TaskStore tasks();

  CardProvider cards();

  <T> void runTask(
      String label,
      java.util.concurrent.Callable<T> work,
      java.util.function.Consumer<T> completed);

  void notifyUser(String message);

  void refreshDevice();

  void home();
}
