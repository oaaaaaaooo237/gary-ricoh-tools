package local.grworld.studio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

final class ImageWorkspace {
  final DesktopApp app;
  final DesktopSession s;
  final SdWorkspace sd;
  boolean factory;

  ImageWorkspace(DesktopApp app) {
    this.app = app;
    s = app.session;
    sd = new SdWorkspace(app);
  }

  Node view() {
    boolean task = sdTask();
    byte[] visual = displayImage();
    StackPane image = DesktopUI.photo(visual, "选择一张图片，开始定制关机画面");
    VBox picture = DesktopUI.box(image);
    VBox.setVgrow(image, Priority.ALWAYS);
    picture
        .getChildren()
        .add(
            DesktopUI.text(
                visual == null
                    ? "输出标准 · 720 × 480 · JPEG"
                    : "720 × 480 · " + visual.length + " 字节 · " + (task ? "本轮 SD 候选 JPEG" : "JPEG"),
                "hint"));
    HBox actions = new HBox(12);
    Button choose = DesktopUI.button("选择图片", this::choose, false),
        edit = DesktopUI.button("画面调整", () -> CropEditor.show(app), false),
        save =
            DesktopUI.button(
                "保存成品",
                () ->
                    app.work(
                        "保存图片库…",
                        () -> {
                          s.saveLibrary(visual);
                          return null;
                        },
                        r -> app.notifyUser("已保存，可从选择图片 → 已处理的关机图重新打开。")),
                false);
    edit.setDisable(s.original == null || s.pending() || task);
    save.setDisable(visual == null || s.pending());
    choose.setDisable(s.pending() || task);
    HBox.setHgrow(choose, Priority.ALWAYS);
    HBox.setHgrow(edit, Priority.ALWAYS);
    HBox.setHgrow(save, Priority.ALWAYS);
    actions.getChildren().addAll(choose, edit, save);
    picture.getChildren().add(actions);
    HBox tools = new HBox(18);
    tools
        .getChildren()
        .addAll(
            app.nav("导出 JPEG", () -> export(false)),
            app.nav("保存图片工程", () -> export(true)),
            app.nav("打开图片工程", this::openProject));
    picture.getChildren().add(tools);
    Node rail;
    if (s.pending()) rail = wifi();
    else if (CameraWorkflowPolicy.route(s.context()) == CameraWorkflowPolicy.SD) rail = sd.view();
    else rail = wifi();
    return DesktopUI.workspace("调整关机画面", picture, rail);
  }

  VBox wifi() {
    if (s.pending()) {
      boolean checked = s.wifiRechecked;
      VBox v =
          DesktopUI.actions(
              checked ? "核对相机实际画面" : "续办 · 重新核实连接",
              checked
                  ? "机型与固件已重新核实。接口无法读取当前关机 JPEG；请实际关机查看画面，再记录结果。程序不会自动重复上传。"
                  : "上次操作结果尚未核对。先重新查询实际相机身份；本地记录不能证明图片已经替换。");
      v.getChildren()
          .add(DesktopUI.text("任务图片 SHA-256\n" + s.state.getProperty("wifi.hash", ""), "hint"));
      if (!checked) v.getChildren().add(DesktopUI.button("重新查询相机", app::refreshDevice, true));
      else {
        v.getChildren()
            .addAll(
                DesktopUI.button("画面正确", () -> actual(1), true),
                DesktopUI.button("不正确或未变化", () -> actual(2), false),
                DesktopUI.button("暂未核对", () -> app.notifyUser("保留待核对，不自动重传。"), false));
      }
      return v;
    }
    CameraIdentity live = s.live(), id = s.context();
    boolean supported = live != null && live.wifiSupported();
    String step = supported ? s.prepared == null ? "选择关机图片" : "确认最终图片" : "识别相机";
    VBox v =
        DesktopUI.actions(
            step,
            supported
                ? "检查左侧最终 JPEG 预览。点击确认后，程序自动复核型号、固件和网络，再上传并检查接口结果。"
                : "电脑连接相机 Wi-Fi 后，程序自动读取实际型号与固件。可先选图编辑；识别合格后才开放相机操作。");
    if (id != null && !id.wifiSupported())
      v.getChildren().add(DesktopUI.text("此机型／固件不适用 Wi-Fi 关机图。GR3 目前仅支持拍摄增强。", "hint"));
    Button upload =
        DesktopUI.button(
            factory ? "确认并恢复原厂画面" : "确认并替换",
            () -> {
              byte[] image = s.prepared == null ? null : s.prepared.clone();
              boolean stock = factory;
              app.work(
                  "复核连接并上传…",
                  () -> {
                    s.send(image, stock);
                    return null;
                  },
                  r -> {
                    factory = false;
                    app.notifyUser(
                        s.transport.simulated() ? "模拟接口已接受；下一步为测试用的实际结果记录。" : "相机接口已接受，请实际关机查看画面。");
                  });
            },
            true);
    upload.setDisable(!supported || s.prepared == null);
    v.getChildren().add(upload);
    Button restore =
        DesktopUI.button(
            "使用本机型原厂画面",
            () -> {
              CameraIdentity c = s.live();
              app.work(
                  "加载对应原厂画面…",
                  () -> {
                    byte[] stock = s.resources.stock(c);
                    s.importImage(stock, "原厂画面", true);
                    return null;
                  },
                  r -> {
                    factory = true;
                    app.notifyUser("已加载对应机型原厂图片，请检查预览后确认恢复。");
                  });
            },
            false);
    restore.setDisable(!supported);
    v.getChildren()
        .addAll(
            restore,
            DesktopUI.rule(),
            DesktopUI.text("GR4／GR4 HDF 1.20 使用 Wi-Fi；GR3X 1.70 会自动进入 SD 工作流。", "hint"),
            DesktopUI.button("检测连接", app::refreshDevice, false));
    return v;
  }

  void actual(int result) {
    app.work(
        "记录实际显示…",
        () -> {
          s.wifiActual(result);
          return null;
        },
        r -> app.notifyUser(result == 1 ? "已记录画面正确，本轮结束。" : "已记录画面未正确变化，可重新选择图片后发起新操作。"));
  }

  void choose() {
    Dialog<Void> d = new Dialog<>();
    d.initOwner(app.stage);
    d.setTitle("选择图片");
    VBox v =
        DesktopUI.box(
            DesktopUI.text("选择图片", "section-title"),
            DesktopUI.text("从本地导入新图片，或选择已经处理好的成品。", "hint"));
    v.setPadding(new Insets(24));
    v.setPrefWidth(430);
    v.getChildren()
        .addAll(
            DesktopUI.button(
                "从文件选择新图",
                () -> {
                  d.close();
                  Path p =
                      DesktopUI.chooseOpen(
                          app.stage, "选择图片", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif");
                  if (p != null) importPath(p, false);
                },
                true),
            DesktopUI.button(
                "已处理的关机图",
                () -> {
                  d.close();
                  library();
                },
                false));
    d.getDialogPane().setContent(v);
    d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
    DesktopUI.style(d.getDialogPane().getScene());
    d.showAndWait();
  }

  void importPath(Path p, boolean exact) {
    app.work(
        "载入图片…",
        () -> {
          if (Files.size(p) > 40 * 1024 * 1024) throw new IOException("图片过大");
          s.importImage(Files.readAllBytes(p), "已选择图片", exact);
          return null;
        },
        r -> {
          factory = false;
          app.notifyUser("图片已准备，可按需调整构图。");
        });
  }

  void library() {
    try {
      Dialog<Void> d = new Dialog<>();
      d.initOwner(app.stage);
      d.setTitle("已处理的关机图");
      VBox v = DesktopUI.box(DesktopUI.text("已处理的关机图", "section-title"));
      v.setPadding(new Insets(20));
      TilePane grid = new TilePane(14, 14);
      grid.setPrefColumns(3);
      grid.setPrefTileWidth(190);
      for (String asset : List.of("GB_GR4.jpg", "GB_HDF.jpg")) {
        byte[] image = s.resources.read(asset);
        addThumbnail(grid, image, d, "原厂预设");
      }
      for (String name : s.store.list("library-"))
        addThumbnail(grid, s.store.read(name), d, "已保存图片");
      v.getChildren()
          .addAll(
              grid,
              DesktopUI.button(
                  "打开已处理的 JPEG",
                  () -> {
                    d.close();
                    Path p = DesktopUI.chooseOpen(app.stage, "打开已处理 JPEG", "*.jpg", "*.jpeg");
                    if (p != null) importPath(p, true);
                  },
                  false));
      ScrollPane scroll = DesktopUI.scroll(v);
      scroll.setPrefSize(660, 450);
      d.getDialogPane().setContent(scroll);
      d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
      DesktopUI.style(d.getDialogPane().getScene());
      d.showAndWait();
    } catch (Exception e) {
      app.error(e);
    }
  }

  void addThumbnail(TilePane grid, byte[] image, Dialog<?> dialog, String title) {
    ImageView thumbnail = new ImageView(new Image(new ByteArrayInputStream(image)));
    thumbnail.setFitWidth(180);
    thumbnail.setFitHeight(120);
    thumbnail.setPreserveRatio(true);
    Button b = new Button();
    b.setGraphic(new VBox(8, thumbnail, DesktopUI.text(title, "hint")));
    b.getStyleClass().add("secondary");
    b.setOnAction(
        e -> {
          dialog.close();
          app.work(
              "载入成品…",
              () -> {
                s.importImage(image, "已处理图片", true);
                return null;
              },
              r -> {
                factory = false;
                app.notifyUser("已载入成品，保留原始 JPEG 字节。");
              });
        });
    grid.getChildren().add(b);
  }

  boolean sdTask() {
    return !s.pending()
        && s.job != null
        && CameraWorkflowPolicy.route(s.context()) == CameraWorkflowPolicy.SD;
  }

  byte[] displayImage() {
    return sdTask() ? s.job.candidate : s.prepared;
  }

  void export(boolean project) {
    byte[] image = displayImage();
    if (image == null) {
      app.notifyUser("请先选择图片。");
      return;
    }
    Path path =
        DesktopUI.chooseSave(
            app.stage,
            project ? "保存图片工程" : "导出 JPEG",
            project ? "GR-EndScreen.grend" : "GR-EndScreen-720x480.jpg");
    if (path == null) return;
    app.work(
        "保存文件…",
        () -> {
          Files.write(path, project ? s.project(image) : image);
          return null;
        },
        r -> app.notifyUser("文件已保存。"));
  }

  void openProject() {
    Path path = DesktopUI.chooseOpen(app.stage, "打开图片工程", "*.grend");
    if (path != null)
      app.work(
          "校验工程…",
          () -> {
            s.openProject(DesktopResources.limited(Files.newInputStream(path), 42 * 1024 * 1024));
            return null;
          },
          r -> {
            factory = false;
            app.notifyUser("工程完整哈希已核实，图片已恢复。");
          });
  }
}
