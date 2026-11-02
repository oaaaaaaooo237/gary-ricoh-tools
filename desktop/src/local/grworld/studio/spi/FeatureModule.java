package local.grworld.studio.spi;

import javafx.scene.Node;

/** Version1 extension contract, discovered through ServiceLoader or explicit registry. */
public interface FeatureModule {
  default int apiVersion() {
    return 1;
  }

  String id();

  String title();

  String description();

  Node createView(FeatureHost host);
}
