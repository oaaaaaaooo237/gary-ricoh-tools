package local.grworld.studio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import local.grworld.studio.spi.*;

public final class DesktopApp extends Application implements FeatureHost {
  public static final String VERSION = "0.1.2-preview";
  DesktopSession session;
  Stage stage;
  BorderPane shell;
  VBox chrome;
  Label device, feedback;
  Circle lamp;
  ProgressBar progress;
  Node page;
  String active = "home";
  boolean busy, checking;
  long epoch;
  final ExecutorService io =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "camera-work");
            t.setDaemon(true);
            return t;
          });
  final ScheduledExecutorService polling =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "camera-monitor");
            t.setDaemon(true);
            return t;
          });
  final Map<String, FeatureModule> features = new LinkedHashMap<>();
  ImageWorkspace imageWorkspace;
  EnhancementWorkspace enhancementWorkspace;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) throws Exception {
    this.stage = stage;
    boolean fixture = getParameters().getRaw().contains("--ui-test");
    String workspace = getParameters().getNamed().get("workspace");
    Path root =
        workspace != null
            ? Path.of(workspace)
            : Path.of(
                System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")),
                "GaryRicohTools");
    Path fixtureRoot = fixture ? root.resolve("fixture") : null;
    if (fixtureRoot != null) Files.createDirectories(fixtureRoot.resolve("card"));
    CameraTransport transport = fixture ? new FixtureCamera(fixtureRoot) : new DesktopCamera();
    session = new DesktopSession(transport, new DesktopStore(root.resolve("tasks")), fixtureRoot);
    imageWorkspace = new ImageWorkspace(this);
    enhancementWorkspace = new EnhancementWorkspace(this);
    register(
        new FeatureModule() {
          public String id() {
            return "shutdown-image";
          }

          public String title() {
            return "调整关机画面";
          }

          public String description() {
            return "选择、裁切与预览图片，更换或恢复原厂关机图。";
          }

          public Node createView(FeatureHost host) {
            return imageWorkspace.view();
          }
        });
    register(
        new FeatureModule() {
          public String id() {
            return "shooting-enhancement";
          }

          public String title() {
            return "拍摄增强";
          }

          public String description() {
            return "XPan 65:24 宽幅与构图辅助线，按需独立开启。";
          }

          public Node createView(FeatureHost host) {
            return enhancementWorkspace.view();
          }
        });
    for (FeatureModule module : ServiceLoader.load(FeatureModule.class)) register(module);
    shell = new BorderPane();
    chrome = new VBox();
    HBox bar = new HBox(20);
    bar.getStyleClass().add("app-bar");
    Label brand = DesktopUI.text("Gary趣味应用", "brand");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    bar.getChildren().addAll(brand, spacer, nav("功能", this::home), nav("设置", this::settings));
    chrome.getChildren().add(bar);
    if (fixture) {
      Label simulation = DesktopUI.text("模拟联机测试 · 相机身份和执行结果为测试数据，不代表实机成功", "fixture");
      chrome.getChildren().add(simulation);
    }
    lamp = new Circle(4, Color.web("#8b8b87"));
    device = DesktopUI.text("选择功能后，自动识别相机型号与固件", "device");
    HBox status = new HBox(10, lamp, device);
    status.getStyleClass().add("device-bar");
    Region statusSpacer = new Region();
    HBox.setHgrow(statusSpacer, Priority.ALWAYS);
    Button detect = nav("检测连接", this::refreshDevice);
    status.getChildren().addAll(statusSpacer, detect);
    chrome.getChildren().add(status);
    shell.setTop(chrome);
    feedback = DesktopUI.text("准备就绪", "feedback");
    progress = new ProgressBar();
    progress.setMaxWidth(Double.MAX_VALUE);
    progress.setVisible(false);
    progress.setManaged(false);
    shell.setBottom(new VBox(progress, feedback));
    Scene scene = new Scene(shell, 1080, 720);
    DesktopUI.style(scene);
    stage.setTitle("Gary趣味应用 · Windows " + VERSION);
    stage.setScene(scene);
    resizeWindow(1080, 720);
    scene.widthProperty().addListener((o, a, b) -> updateDensity(scene));
    scene.heightProperty().addListener((o, a, b) -> updateDensity(scene));
    updateDensity(scene);
    stage.show();
    home();
    stage
        .focusedProperty()
        .addListener(
            (o, old, focused) -> {
              if (focused && shouldMonitor()) refreshDevice();
            });
    polling.scheduleWithFixedDelay(
        () ->
            Platform.runLater(
                () -> {
                  if (shouldMonitor()
                      && !busy
                      && !checking
                      && stage.isShowing()
                      && !stage.isIconified()) refreshDevice();
                }),
        5,
        5,
        TimeUnit.SECONDS);
  }

  boolean shouldMonitor() {
    return session.sessionIdentity != null || features.containsKey(active);
  }

  void resizeWindow(double width, double height) {
    // JavaFX screen bounds are logical units and already include Windows DPI scaling.
    Rectangle2D bounds =
        (stage.isShowing()
                ? Screen.getScreensForRectangle(
                    stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())
                : List.of(Screen.getPrimary()))
            .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
    double availableWidth = Math.max(1, bounds.getWidth() - 24);
    double availableHeight = Math.max(1, bounds.getHeight() - 24);
    stage.setMinWidth(Math.min(880, availableWidth));
    stage.setMinHeight(Math.min(600, availableHeight));
    stage.setWidth(Math.min(width, availableWidth));
    stage.setHeight(Math.min(height, availableHeight));
    stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
    stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);
  }

  void updateDensity(Scene scene) {
    boolean compact = scene.getWidth() < 1000 || scene.getHeight() < 650;
    if (compact && !shell.getStyleClass().contains("compact")) shell.getStyleClass().add("compact");
    else if (!compact) shell.getStyleClass().remove("compact");
  }

  Button nav(String title, Runnable r) {
    Button b = new Button(title);
    b.getStyleClass().add("nav");
    b.setOnAction(
        e -> {
          if (!busy) r.run();
        });
    return b;
  }

  void register(FeatureModule f) {
    if (f.apiVersion() != 1
        || !f.id().matches("[a-z][a-z0-9-]{1,60}")
        || features.putIfAbsent(f.id(), f) != null)
      throw new IllegalArgumentException("功能接口版本或标识无效");
  }

  void open(String id) {
    if (busy) return;
    active = id;
    epoch++;
    if (id.equals("shutdown-image")) session.sdVerified = false;
    if (id.equals("shooting-enhancement")) {
      session.enhCardChecked = false;
      session.enhReportChecked = false;
      enhancementWorkspace.configuring = false;
    }
    render();
    refreshDevice();
  }

  public void home() {
    if (busy) return;
    active = "home";
    epoch++;
    VBox content = new VBox(20);
    content.getStyleClass().add("home");
    Label intro = DesktopUI.text("为理光 GR4、GR4 HDF、GR3、GR3X 系列相机提供关机画面定制与恢复、XPan 宽幅和构图辅助线。", "lead");
    intro.setMaxWidth(850);
    content.getChildren().addAll(intro, DesktopUI.text("选择功能", "section-title"), DesktopUI.rule());
    for (FeatureModule f : features.values()) {
      VBox copy =
          DesktopUI.box(
              DesktopUI.text(f.title(), "entry-title"), DesktopUI.text(f.description(), "hint"));
      HBox row = new HBox(25);
      row.setAlignment(Pos.CENTER_LEFT);
      copy.setMinWidth(0);
      row.setMinWidth(0);
      row.setMinHeight(64);
      row.setPrefHeight(64);
      row.setMaxHeight(Region.USE_PREF_SIZE);
      copy.setMaxHeight(Region.USE_PREF_SIZE);
      HBox.setHgrow(copy, Priority.ALWAYS);
      Label arrow = DesktopUI.text("›", "entry-arrow");
      arrow.setMinWidth(Region.USE_PREF_SIZE);
      arrow.setMaxWidth(Region.USE_PREF_SIZE);
      arrow.setMaxHeight(Region.USE_PREF_SIZE);
      row.getChildren().addAll(copy, arrow);
      Button button = new Button();
      button.setGraphic(row);
      button.setMaxWidth(Double.MAX_VALUE);
      button.setMinHeight(104);
      button.setPrefHeight(104);
      button.setMaxHeight(Region.USE_PREF_SIZE);
      button.getStyleClass().add("home-entry");
      button.setAccessibleText(f.title());
      // A graphic's preferred height must use the available width, rather than
      // wrapping against its initial zero-width maximum during the first layout.
      row.prefWidthProperty()
          .bind(javafx.beans.binding.Bindings.max(200, shell.widthProperty().subtract(136)));
      button.setOnAction(e -> open(f.id()));
      content.getChildren().add(button);
    }
    content.getChildren().add(DesktopUI.text("GR3 仅支持拍摄增强。进入功能后由程序识别实际型号和固件，选择适用的方法。", "hint"));
    page = DesktopUI.scroll(content);
    shell.setCenter(page);
    device();
  }

  void render() {
    if (active.equals("home")) {
      home();
      return;
    }
    if (active.equals("settings")) {
      settings();
      return;
    }
    FeatureModule module = features.get(active);
    if (module != null) {
      page = module.createView(this);
      page.setDisable(busy);
      shell.setCenter(page);
    }
    device();
  }

  void device() {
    CameraIdentity actual = session.live();
    int color =
        actual == null
            ? 0x8b8b87
            : (active.equals("home") || active.equals("settings")
                    ? actual.supported()
                    : active.equals("shooting-enhancement")
                        ? actual.enhancementSupported()
                        : actual.wifiSupported() || actual.sdSupported())
                ? 0x38c86b
                : 0xef7575;
    lamp.setFill(Color.rgb((color >> 16) & 255, (color >> 8) & 255, color & 255));
    device.setText(
        actual == null
            ? session.sessionIdentity == null
                ? "未连接相机 · 固件 —"
                : "未连接 · 本次已识别 "
                    + session.sessionIdentity.model
                    + " · "
                    + session.sessionIdentity.firmware
            : actual.model + " · 固件 " + actual.firmware);
  }

  <T> void work(String label, Callable<T> action, Consumer<T> success) {
    if (busy) return;
    busy = true;
    long captured = epoch;
    notifyUser(label);
    progress.setVisible(true);
    progress.setManaged(true);
    if (page != null) page.setDisable(true);
    io.submit(
        () -> {
          T result = null;
          Throwable failure = null;
          try {
            result = action.call();
          } catch (Throwable e) {
            failure = e;
          }
          T value = result;
          Throwable error = failure;
          Platform.runLater(
              () -> {
                busy = false;
                progress.setVisible(false);
                progress.setManaged(false);
                if (page != null) page.setDisable(false);
                if (error == null) {
                  try {
                    success.accept(value);
                  } catch (Throwable e) {
                    error(e);
                  }
                } else error(error);
                if (epoch == captured && !busy) render();
              });
        });
  }

  void error(Throwable e) {
    String message = e.getMessage();
    notifyUser(message == null ? "操作未完成，请重新检查" : message);
    feedback.getStyleClass().remove("success");
    if (!feedback.getStyleClass().contains("error")) feedback.getStyleClass().add("error");
  }

  public void notifyUser(String message) {
    feedback.setText(message);
    feedback.getStyleClass().remove("error");
  }

  String deviceSignature() {
    CameraIdentity c = session.live();
    return (c == null ? "-" : c.model + "/" + c.firmware) + "/" + session.wifiRechecked;
  }

  public void refreshDevice() {
    if (busy || checking) return;
    checking = true;
    long captured = epoch;
    String before = deviceSignature();
    io.submit(
        () -> {
          Throwable failed = null;
          try {
            session.observe();
          } catch (Throwable e) {
            failed = e;
          }
          Throwable error = failed;
          Platform.runLater(
              () -> {
                checking = false;
                device();
                if (epoch == captured && !active.equals("home") && !active.equals("settings")) {
                  if (error != null && !before.equals(deviceSignature()))
                    notifyUser("未连接相机。电脑需连接相机 Wi-Fi；图片编辑和本地保存仍可使用。");
                  if (!busy && !before.equals(deviceSignature())) render();
                }
              });
        });
  }

  void settings() {
    if (busy) return;
    active = "settings";
    epoch++;
    VBox body = new VBox(18);
    body.getStyleClass().add("home");
    body.getChildren()
        .addAll(
            DesktopUI.text("设置", "section-title"),
            DesktopUI.text("相机网络", "step-title"),
            DesktopUI.text(
                "让相机接口走连接相机的网卡；有线网络或第二网卡可继续用于上网。USB 读卡器用于 SD 任务，不等同于相机 USB 换图。", "hint"));
    if (session.transport instanceof DesktopCamera camera) {
      ComboBox<DesktopCamera.Adapter> adapters = new ComboBox<>();
      adapters.setPromptText("自动选择相机网络");
      try {
        adapters.getItems().addAll(DesktopCamera.adapters());
      } catch (Exception e) {
        error(e);
      }
      adapters.setMaxWidth(600);
      adapters.getSelectionModel().select(camera.selected);
      adapters.setOnAction(
          e -> {
            camera.selected = adapters.getValue();
            session.current = null;
            session.wifiRechecked = false;
            device();
          });
      body.getChildren().add(adapters);
    }
    body.getChildren()
        .addAll(
            DesktopUI.button("重新检测相机", this::refreshDevice, false),
            DesktopUI.rule(),
            DesktopUI.text("显示与记录", "step-title"),
            DesktopUI.button("使用紧凑窗口", () -> resizeWindow(920, 640), false),
            DesktopUI.button("恢复标准窗口", () -> resizeWindow(1080, 720), false),
            DesktopUI.button(
                "查看本次操作记录",
                () -> DesktopUI.longText(stage, "操作记录", session.state.toString()),
                false),
            DesktopUI.button("关于与开源声明", this::license, false));
    page = DesktopUI.scroll(body);
    shell.setCenter(page);
    device();
  }

  void license() {
    try {
      String legal =
          new String(
              DesktopResources.limited(
                  Objects.requireNonNull(getClass().getResourceAsStream("/LICENSE")), 64 * 1024),
              StandardCharsets.UTF_8);
      String upstream =
          new String(
              session.resources.bundle(new CameraIdentity("RICOH GR IV HDF", "1.20")).license,
              StandardCharsets.UTF_8);
      DesktopUI.longText(
          stage,
          "关于与开源声明",
          "Gary趣味应用 · Windows "
              + VERSION
              + "\n"
              + "项目代码采用 MIT 许可。拍摄增强基于 ahonn/GRKit1.3.5；保留其作者、贡献者及第三方许可。\n"
              + "https://github.com/ahonn/GRKit\n"
              + "SD 换图研究参考 radium-wang 项目。图片和固件资源不适用本项目 MIT 许可。\n\n"
              + legal
              + "\n\n"
              + upstream);
    } catch (Exception e) {
      error(e);
    }
  }

  public CameraTransport camera() {
    return session.transport;
  }

  public ImageProcessor images() {
    return session.images;
  }

  public TaskStore tasks() {
    return session.store;
  }

  public CardProvider cards() {
    return new CardProvider() {
      public List<Volume> volumes() {
        List<Path> paths =
            session.fixtureRoot == null
                ? DesktopCard.volumes()
                : List.of(session.fixtureRoot.resolve("card"));
        return paths.stream().map(p -> new Volume(p.toString(), p.toString())).toList();
      }

      public CardAccess open(String id, boolean writable) throws Exception {
        if (volumes().stream().noneMatch(v -> v.id().equals(id))) throw new IOException("卷已断开或不适用");
        return session.card(Path.of(id), writable);
      }
    };
  }

  public <T> void runTask(String label, Callable<T> task, Consumer<T> completed) {
    work(label, task, completed);
  }

  @Override
  public void stop() {
    polling.shutdownNow();
    io.shutdownNow();
    try {
      session.transport.close();
    } catch (Exception ignored) {
    }
  }
}
