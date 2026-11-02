package local.grworld.studio;

import javafx.geometry.*;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import local.grworld.studio.spi.ImageProcessor;

final class CropEditor {
  static void show(DesktopApp app) {
    DesktopSession s = app.session;
    if (s.original == null) return;
    try {
      Dialog<Void> dialog = new Dialog<>();
      dialog.initOwner(app.stage);
      dialog.setTitle("画面调整");
      Image source = DesktopImages.fx(s.original);
      Canvas canvas = new Canvas(650, 410);
      ComboBox<String> mode = new ComboBox<>();
      mode.getItems().addAll("完整显示", "裁切填满", "拉伸填满", "原尺寸加底");
      String[] keys = {"fit", "crop", "stretch", "original"};
      for (int i = 0; i < keys.length; i++)
        if (keys[i].equals(s.edit.mode())) mode.getSelectionModel().select(i);
      if (mode.getValue() == null) mode.getSelectionModel().selectFirst();
      ComboBox<String> bg = new ComboBox<>();
      bg.getItems().addAll("黑色", "灰色", "白色");
      bg.getSelectionModel()
          .select(s.edit.background() == 0x808080 ? 1 : s.edit.background() == 0xffffff ? 2 : 0);
      Slider zoom = new Slider(1, 3, s.edit.zoom()),
          quality = new Slider(30, 100, s.edit.quality());
      double[] pan = {s.edit.panX(), s.edit.panY(), 0, 0, 0, 0};
      Runnable draw =
          () -> {
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setFill(Color.web("#101010"));
            g.fillRect(0, 0, 650, 410);
            double scale = Math.min(630 / source.getWidth(), 390 / source.getHeight()),
                w = source.getWidth() * scale,
                h = source.getHeight() * scale,
                x = (650 - w) / 2,
                y = (410 - h) / 2;
            g.drawImage(source, x, y, w, h);
            if (mode.getSelectionModel().getSelectedIndex() == 1) {
              double base = Math.max(720 / source.getWidth(), 480 / source.getHeight()),
                  rw = 720 / base / zoom.getValue() * scale,
                  rh = 480 / base / zoom.getValue() * scale,
                  rx = x + (w - rw) / 2 * (1 - pan[0]),
                  ry = y + (h - rh) / 2 * (1 - pan[1]);
              g.setFill(Color.color(0, 0, 0, .6));
              g.fillRect(x, y, w, Math.max(0, ry - y));
              g.fillRect(x, ry + rh, w, Math.max(0, y + h - ry - rh));
              g.fillRect(x, ry, Math.max(0, rx - x), rh);
              g.fillRect(rx + rw, ry, Math.max(0, x + w - rx - rw), rh);
              g.setStroke(Color.WHITE);
              g.setLineWidth(2);
              g.strokeRect(rx, ry, rw, rh);
              g.setLineWidth(.7);
              for (int i = 1; i < 3; i++) {
                g.strokeLine(rx + rw * i / 3, ry, rx + rw * i / 3, ry + rh);
                g.strokeLine(rx, ry + rh * i / 3, rx + rw, ry + rh * i / 3);
              }
              pan[4] = Math.max(1, w - rw);
              pan[5] = Math.max(1, h - rh);
            }
          };
      canvas.setOnMousePressed(
          e -> {
            pan[2] = e.getX();
            pan[3] = e.getY();
          });
      canvas.setOnMouseDragged(
          e -> {
            if (mode.getSelectionModel().getSelectedIndex() != 1) return;
            pan[0] = Math.max(-1, Math.min(1, pan[0] - (e.getX() - pan[2]) * 2 / pan[4]));
            pan[1] = Math.max(-1, Math.min(1, pan[1] - (e.getY() - pan[3]) * 2 / pan[5]));
            pan[2] = e.getX();
            pan[3] = e.getY();
            draw.run();
          });
      mode.setOnAction(e -> draw.run());
      zoom.valueProperty().addListener((o, a, b) -> draw.run());
      VBox controls =
          DesktopUI.box(
              DesktopUI.text("画面调整", "section-title"),
              DesktopUI.text("裁切填满时，拖动白框选择保留区域。应用后主页面显示实际最终 JPEG。", "hint"),
              canvas);
      HBox choices =
          new HBox(18, DesktopUI.text("显示方式", "hint"), mode, DesktopUI.text("背景", "hint"), bg);
      controls
          .getChildren()
          .addAll(
              choices,
              DesktopUI.text("裁切范围 / 缩放", "hint"),
              zoom,
              DesktopUI.text("JPEG 画质", "hint"),
              quality,
              DesktopUI.button(
                  "居中裁切",
                  () -> {
                    pan[0] = pan[1] = 0;
                    zoom.setValue(1);
                    draw.run();
                  },
                  false));
      controls.setPadding(new Insets(20));
      dialog.getDialogPane().setContent(DesktopUI.scroll(controls));
      ButtonType apply = new ButtonType("应用调整", ButtonBar.ButtonData.OK_DONE);
      dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, apply);
      DesktopUI.style(dialog.getDialogPane().getScene());
      dialog
          .getDialogPane()
          .lookupButton(apply)
          .addEventFilter(
              javafx.event.ActionEvent.ACTION,
              e -> {
                int background =
                    new int[] {0, 0x808080, 0xffffff}[bg.getSelectionModel().getSelectedIndex()];
                ImageProcessor.Edit edit =
                    new ImageProcessor.Edit(
                        keys[mode.getSelectionModel().getSelectedIndex()],
                        background,
                        zoom.getValue(),
                        pan[0],
                        pan[1],
                        (int) quality.getValue());
                dialog.close();
                app.work(
                    "生成最终预览…",
                    () -> {
                      s.process(edit);
                      return null;
                    },
                    r -> {
                      app.imageWorkspace.factory = false;
                      app.notifyUser("调整已应用，预览对应实际输出 JPEG。");
                    });
              });
      draw.run();
      dialog.showAndWait();
    } catch (Exception e) {
      app.error(e);
    }
  }
}
