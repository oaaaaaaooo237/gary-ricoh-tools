package local.grworld.studio;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

/** One library for bundled and imported effects; choosing a look does not write a card. */
final class EffectPicker {
  final DesktopApp app;
  final CameraIdentity identity;
  final Set<String> selected;
  final Consumer<Set<String>> accepted;
  final ListView<DesktopEffects.Effect> list = new ListView<>();
  final ImageView image = new ImageView();
  final Label placeholder = DesktopUI.text("选择照片后显示近似色彩效果", "hint");
  final Label description = DesktopUI.text("勾选要准备到 SD 卡的风格。选择一张照片可查看近似效果。", "hint");
  final Label count = DesktopUI.text("", "hint");
  BufferedImage photo;
  Dialog<ButtonType> dialog;

  EffectPicker(
      DesktopApp app,
      CameraIdentity identity,
      Set<String> selected,
      Consumer<Set<String>> accepted) {
    this.app = app;
    this.identity = identity;
    this.selected = new TreeSet<>(selected);
    this.accepted = accepted;
  }

  void show() {
    app.work(
        "读取本地胶片库…",
        () -> app.session.effects.list(identity, app.session.resources.bundle(identity)),
        effects -> {
          dialog = new Dialog<>();
          dialog.initOwner(app.stage);
          dialog.setTitle("选择胶片风格");
          dialog.setHeaderText("内置胶片与自定义风格共用这个列表");
          list.getStyleClass().add("effect-list");
          list.getItems().setAll(effects);
          list.setPrefWidth(245);
          list.setPrefHeight(340);
          selected.retainAll(effects.stream().map(e -> e.file).toList());
          list.setCellFactory(
              view ->
                  new ListCell<>() {
                    protected void updateItem(DesktopEffects.Effect effect, boolean empty) {
                      super.updateItem(effect, empty);
                      if (empty || effect == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                      }
                      CheckBox box = new CheckBox(effect.toString());
                      box.setSelected(selected.contains(effect.file));
                      box.setMaxWidth(Double.MAX_VALUE);
                      box.setOnAction(
                          e -> {
                            if (box.isSelected()) selected.add(effect.file);
                            else selected.remove(effect.file);
                            list.getSelectionModel().select(effect);
                            updateCount();
                            render();
                          });
                      setGraphic(box);
                      setText(null);
                    }
                  });
          list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> render());
          image.setFitWidth(375);
          image.setFitHeight(180);
          image.setPreserveRatio(true);
          StackPane well = new StackPane(image, placeholder);
          well.getStyleClass().add("photo-well");
          well.setMinHeight(160);
          well.setPrefHeight(190);
          Button choose = DesktopUI.button("选择预览照片", this::choosePhoto, false);
          ScrollPane details = new ScrollPane(description);
          details.setFitToWidth(true);
          details.setPrefHeight(100);
          details.setMinHeight(90);
          VBox preview = new VBox(10, well, choose, details);
          preview.setPrefWidth(385);
          preview.setMinWidth(280);
          HBox body = new HBox(18, list, preview);
          HBox.setHgrow(preview, Priority.ALWAYS);
          Button imported = DesktopUI.button("导入自定义滤镜", this::importEffect, false);
          Button lightroom = DesktopUI.button("Lightroom 预设转换", this::lightroom, false);
          VBox content =
              DesktopUI.box(
                  body,
                  count,
                  new HBox(12, imported, lightroom),
                  DesktopUI.text("预览为近似色彩示意，建议使用标准模式 JPEG。真实效果仍需在相机试拍确认。", "hint"));
          dialog.getDialogPane().setContent(content);
          ButtonType save = new ButtonType("使用所选风格", ButtonBar.ButtonData.OK_DONE);
          dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
          Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
          saveButton.setMinWidth(142);
          ButtonBar.setButtonUniformSize(saveButton, false);
          dialog.getDialogPane().setPrefWidth(730);
          ((Button) dialog.getDialogPane().lookupButton(save))
              .disableProperty()
              .bind(
                  javafx.beans.binding.Bindings.createBooleanBinding(
                      () -> selected.isEmpty() || selected.size() > 40, count.textProperty()));
          DesktopUI.style(dialog.getDialogPane().getScene());
          updateCount();
          if (!effects.isEmpty()) list.getSelectionModel().selectFirst();
          dialog
              .showAndWait()
              .filter(value -> value == save)
              .ifPresent(value -> accepted.accept(new TreeSet<>(selected)));
        });
  }

  void updateCount() {
    count.setText("已选 " + selected.size() + " 个风格 · 最多 40 个，机内 GRK 菜单切换");
  }

  void render() {
    DesktopEffects.Effect effect = list.getSelectionModel().getSelectedItem();
    if (effect == null) return;
    String detail = effect.name + "\n" + (effect.custom ? effect.origin : "内置胶片风格");
    if (effect.custom) {
      detail +=
          "\n拟合程度："
              + switch (effect.grade) {
                case "A" -> "接近原文件";
                case "B" -> "较接近";
                case "C" -> "存在明显差异";
                default -> "差异较大，需仔细试拍";
              };
      detail += "\n输入色彩：" + effect.input;
      if (!effect.warnings.isEmpty()) detail += "\n转换提醒：" + String.join("；", effect.warnings);
    } else if (!effect.license.isEmpty()) detail += "\n来源许可：" + effect.license;
    description.setText(detail);
    placeholder.setVisible(photo == null);
    if (photo != null) {
      BufferedImage rendered = renderApproximate(photo, effect.preview);
      WritableImage converted = new WritableImage(rendered.getWidth(), rendered.getHeight());
      converted
          .getPixelWriter()
          .setPixels(
              0,
              0,
              rendered.getWidth(),
              rendered.getHeight(),
              PixelFormat.getIntArgbInstance(),
              rendered.getRGB(
                  0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth()),
              0,
              rendered.getWidth());
      image.setImage(converted);
    }
  }

  static BufferedImage renderApproximate(BufferedImage original, byte[] table) {
    int w = Math.min(420, original.getWidth()),
        h = Math.max(1, (int) ((double) original.getHeight() * w / original.getWidth()));
    if (h > 300) {
      w = Math.max(1, w * 300 / h);
      h = 300;
    }
    BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        int rgb = original.getRGB(x * original.getWidth() / w, y * original.getHeight() / h);
        double[] p = {
          ((rgb >> 16) & 255) * 32.0 / 255,
          ((rgb >> 8) & 255) * 32.0 / 255,
          (rgb & 255) * 32.0 / 255
        };
        int[] lo = {(int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2])};
        int output = 0;
        for (int channel = 0; channel < 3; channel++) {
          double value = 0;
          for (int corner = 0; corner < 8; corner++) {
            int[] v = lo.clone();
            double weight = 1;
            for (int axis = 0; axis < 3; axis++) {
              double t = p[axis] - lo[axis];
              if ((corner & (1 << axis)) != 0) {
                v[axis] = Math.min(32, lo[axis] + 1);
                weight *= t;
              } else weight *= 1 - t;
            }
            value += weight * (table[((v[2] * 33 + v[1]) * 33 + v[0]) * 3 + channel] & 255);
          }
          output = (output << 8) | Math.min(255, Math.max(0, (int) Math.round(value)));
        }
        result.setRGB(x, y, output);
      }
    return result;
  }

  void choosePhoto() {
    Path path = DesktopUI.chooseOpen(app.stage, "选择用于色彩示意的照片", "*.jpg", "*.jpeg", "*.png");
    if (path == null) return;
    try {
      photo =
          app.session.images.decode(
              DesktopResources.limited(Files.newInputStream(path), 32 * 1024 * 1024));
      render();
    } catch (Exception e) {
      description.setText("照片未载入：" + e.getMessage());
    }
  }

  void importEffect() {
    Path source =
        DesktopUI.chooseOpen(
            app.stage, "导入 LUT 或已应用预设的 Hald 图片", "*.cube", "*.png", "*.tif", "*.tiff");
    if (source == null) return;
    Dialog<String[]> options = new Dialog<>();
    options.initOwner(app.stage);
    options.setTitle("导入自定义滤镜");
    options.setHeaderText(source.getFileName().toString());
    TextField name =
        new TextField(
            source
                .getFileName()
                .toString()
                .replaceFirst("\\.[^.]*$", "")
                .replaceAll("[^A-Za-z0-9 _-]", "")
                .trim());
    if (name.getText().isEmpty()) name.setText("My Look");
    if (name.getText().length() > 16) name.setText(name.getText().substring(0, 16));
    ComboBox<String> encoding = new ComboBox<>();
    encoding
        .getItems()
        .addAll("自动识别", "标准JPEG", "sRGB", "线性", "V-Log", "F-Log", "F-Log2", "F-Log2 C");
    encoding.getSelectionModel().selectFirst();
    options
        .getDialogPane()
        .setContent(
            DesktopUI.box(
                DesktopUI.text("相机风格名：1～16 个英文、数字或空格", "hint"),
                name,
                DesktopUI.text("输入色彩空间；自动识别后仍需检查预览", "hint"),
                encoding,
                DesktopUI.text(
                    "支持 .cube 和 Hald PNG/TIFF。普通照片不是滤镜；Lightroom 预设需先通过 Hald 模板转换。", "hint")));
    ButtonType convert = new ButtonType("转换并加入本地库", ButtonBar.ButtonData.OK_DONE);
    options.getDialogPane().getButtonTypes().addAll(convert, ButtonType.CANCEL);
    options.getDialogPane().lookupButton(convert).setStyle("-fx-min-width: 164px;");
    ButtonBar.setButtonUniformSize(options.getDialogPane().lookupButton(convert), false);
    options
        .getDialogPane()
        .lookupButton(convert)
        .disableProperty()
        .bind(
            javafx.beans.binding.Bindings.createBooleanBinding(
                () ->
                    !name.getText().matches("[\\x20-\\x7e]{1,16}")
                        || !name.getText().equals(name.getText().trim()),
                name.textProperty()));
    options.setResultConverter(
        type ->
            type == convert
                ? new String[] {
                  name.getText(),
                  new String[] {
                        "auto", "standard", "srgb", "linear", "vlog", "flog", "flog2", "flog2c"
                      }
                      [encoding.getSelectionModel().getSelectedIndex()]
                }
                : null);
    DesktopUI.style(options.getDialogPane().getScene());
    options
        .showAndWait()
        .ifPresent(
            values -> {
              dialog.getDialogPane().setDisable(true);
              description.setText("正在本机拟合滤镜，请稍候…");
              app.work(
                  "在本机转换自定义滤镜…",
                  () -> {
                    try {
                      DesktopEffects.Effect effect =
                          DesktopLutConverter.convert(
                              app.session.store, identity, source, values[0], values[1]);
                      app.session.effects.save(identity, effect);
                      return new Object[] {
                        effect,
                        app.session.effects.list(identity, app.session.resources.bundle(identity))
                      };
                    } catch (Exception e) {
                      return new Object[] {e};
                    }
                  },
                  result -> {
                    dialog.getDialogPane().setDisable(false);
                    if (result[0] instanceof Exception error) {
                      description.setText("导入未完成：" + error.getMessage());
                      return;
                    }
                    DesktopEffects.Effect effect = (DesktopEffects.Effect) result[0];
                    @SuppressWarnings("unchecked")
                    List<DesktopEffects.Effect> effects = (List<DesktopEffects.Effect>) result[1];
                    selected.add(effect.file);
                    list.getItems().setAll(effects);
                    updateCount();
                    list.getSelectionModel()
                        .select(
                            effects.stream()
                                .filter(e -> e.file.equals(effect.file))
                                .findFirst()
                                .orElse(null));
                    render();
                  });
            });
  }

  void lightroom() {
    Dialog<Void> help = new Dialog<>();
    help.initOwner(app.stage);
    help.setTitle("Lightroom 预设转换");
    help.setHeaderText("先把预设应用到 Hald 模板，再导入导出的图片");
    Button save =
        DesktopUI.button(
            "保存 Hald 模板 PNG",
            () -> {
              Path target = DesktopUI.chooseSave(app.stage, "保存 Hald 模板", "GRKit-HALD-12.png");
              if (target != null)
                app.work(
                    "生成 Hald 模板…",
                    () -> {
                      Files.write(target, DesktopLutConverter.hald(app.session.store));
                      return null;
                    },
                    r -> app.notifyUser("Hald 模板已保存，请在 Lightroom 应用预设并导出 PNG/TIFF。"));
            },
            true);
    Label instructions =
        DesktopUI.text(
            "1. 保存下面的 Hald 模板，在 Lightroom 中打开。\n\n"
                + "2. 应用预设；不要裁切、缩放、锐化或增加颗粒。局部蒙版、修复和模糊不能转换成色彩滤镜。\n\n"
                + "3. 以原尺寸导出 sRGB PNG 或 TIFF。\n\n"
                + "4. 回到「导入自定义滤镜」选择导出的图片，输入色彩选择 sRGB。\n\n"
                + "机内转换是近似拟合，必须查看预览并实拍。",
            "body");
    instructions.setMinHeight(Region.USE_PREF_SIZE);
    instructions.setPrefWidth(478);
    help.getDialogPane().setContent(DesktopUI.box(instructions, save));
    help.getDialogPane().setPrefWidth(510);
    help.getDialogPane()
        .getButtonTypes()
        .add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));
    DesktopUI.style(help.getDialogPane().getScene());
    help.showAndWait();
  }
}
