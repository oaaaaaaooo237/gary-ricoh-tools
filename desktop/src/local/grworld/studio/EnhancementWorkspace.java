package local.grworld.studio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

final class EnhancementWorkspace {
  final DesktopApp app;
  final DesktopSession s;
  boolean framing = true, guides = true, configuring;
  Path cardPath;
  String style = "三分线", lineColor = "白色", mask = "半透明遮幅";
  double opacity = 1;

  EnhancementWorkspace(DesktopApp app) {
    this.app = app;
    s = app.session;
  }

  Node view() {
    CameraIdentity id = s.context();
    boolean supported = id != null && id.enhancementSupported(), has = s.hasEnh();
    if (has && !configuring) {
      framing = Boolean.parseBoolean(s.state.getProperty("enh.f"));
      guides = Boolean.parseBoolean(s.state.getProperty("enh.g"));
    }
    VBox photo = new VBox(18);
    Canvas canvas = new Canvas(650, 390);
    StackPane well = new StackPane(canvas);
    well.getStyleClass().add("photo-well");
    well.setMinHeight(200);
    well.setPrefHeight(420);
    canvas.widthProperty().bind(well.widthProperty().subtract(24));
    canvas.heightProperty().bind(well.heightProperty().subtract(24));
    Runnable draw = () -> draw(canvas);
    canvas.widthProperty().addListener((o, a, b) -> draw.run());
    canvas.heightProperty().addListener((o, a, b) -> draw.run());
    photo
        .getChildren()
        .addAll(well, DesktopUI.text("构图示意，非相机实时取景。实际比例、线型与开关在相机 GRK / Fn 菜单设置。", "hint"));
    VBox.setVgrow(well, Priority.ALWAYS);
    TitledPane options = new TitledPane();
    options.setText("调整示意样式");
    options.setExpanded(false);
    ComboBox<String> lines = new ComboBox<>();
    lines.getItems().addAll("三分线", "中心十字", "黄金分割", "对角线", "三分＋中心");
    lines.setValue(style);
    ComboBox<String> colors = new ComboBox<>();
    colors.getItems().addAll("白色", "黑色", "黄色");
    colors.setValue(lineColor);
    ComboBox<String> masks = new ComboBox<>();
    masks.getItems().addAll("半透明遮幅", "黑色遮幅", "仅框线");
    masks.setValue(mask);
    Slider alpha = new Slider(.5, 1, opacity);
    lines.setOnAction(
        e -> {
          style = lines.getValue();
          draw.run();
        });
    colors.setOnAction(
        e -> {
          lineColor = colors.getValue();
          draw.run();
        });
    masks.setOnAction(
        e -> {
          mask = masks.getValue();
          draw.run();
        });
    alpha
        .valueProperty()
        .addListener(
            (o, a, b) -> {
              opacity = b.doubleValue();
              draw.run();
            });
    options.setContent(DesktopUI.box(new HBox(12, lines, colors, masks), alpha));
    photo.getChildren().add(options);
    draw.run();
    boolean matched = has && id != null && id.same(s.enhIdentity());
    String title =
        !supported
            ? "识别相机"
            : has && !matched
                ? "已有其他机型的任务"
                : !has || configuring
                    ? "选择增强模块"
                    : !s.enhCardChecked
                        ? "续办 · 核实实际卡片"
                        : !s.enhReportChecked ? "在相机执行并读回" : complete() ? "本轮核对完成" : "核对实际拍摄效果";
    String note =
        !supported
            ? "电脑连接相机 Wi-Fi 后，程序识别实际型号与固件；识别合格再准备。"
            : has && !matched
                ? "当前记录属于 "
                    + s.enhIdentity().model
                    + " · "
                    + s.enhIdentity().firmware
                    + "。请连接原相机续办，或为当前相机建立独立任务。"
                : !has || configuring
                    ? "选择 XPan 宽幅、辅助线或两者。共用一次 SD 准备，日常在相机菜单切换。"
                    : !s.enhCardChecked
                        ? "请读取原任务的实际 SD 卡，核实文件与本轮运行报告。本地记录不能代替相机执行结果。"
                        : !s.enhReportChecked
                            ? "安全弹出卡并放回相机。首次按住 MENU 开机，等待自动关机后正常开机。等待 GRKit 加载，在 ADJ 第六项 GRK"
                                + " 设置模块，之后把卡接回电脑读报告。"
                            : complete()
                                ? "卡文件与本轮报告已核对；实际显示与试拍结果由你记录为正确。"
                                : "请实际查看相机取景和试拍结果，分别记录已选模块的显示。报告通过不等于实际效果已确认。";
    VBox rail = DesktopUI.actions(title, note);
    if (has && !matched && supported)
      rail.getChildren()
          .add(
              DesktopUI.button(
                  "为当前相机建立新任务",
                  () -> {
                    app.work(
                        "保存原任务记录…",
                        () -> {
                          s.store.properties(
                              "enh-history-" + s.state.getProperty("enh.id") + ".properties",
                              s.state);
                          s.state.remove("enh.id");
                          s.persist();
                          return null;
                        },
                        r -> {
                          configuring = true;
                          app.notifyUser("原任务已保留，选择当前机型的模块。");
                        });
                  },
                  true));
    ComboBox<Path> cards = DesktopUI.cards(app);
    if (cardPath != null && cards.getItems().contains(cardPath)) cards.setValue(cardPath);
    cards.setOnAction(e -> cardPath = cards.getValue());
    cardPath = cards.getValue();
    if (!has || configuring) {
      CheckBox f = new CheckBox("XPan 宽幅 · 65:24"), g = new CheckBox("构图辅助线");
      f.setSelected(framing);
      g.setSelected(guides);
      f.setOnAction(
          e -> {
            framing = f.isSelected();
            draw.run();
          });
      g.setOnAction(
          e -> {
            guides = g.isSelected();
            draw.run();
          });
      rail.getChildren().addAll(f, g, cards);
      Button prepare = DesktopUI.button("准备到 SD 卡", () -> prepare(false), true);
      prepare.setDisable(!supported);
      Button manual = DesktopUI.button("系统无法写卡？手动导出 ZIP", this::manual, false);
      manual.setDisable(!supported);
      rail.getChildren().addAll(prepare, manual);
    } else if (matched) {
      if (!s.enhReportChecked) {
        rail.getChildren().add(cards);
        Button read = DesktopUI.button("读取卡内任务与运行报告", () -> read(false), true);
        rail.getChildren().add(read);
        if (Boolean.parseBoolean(s.state.getProperty("enh.manual")))
          rail.getChildren().add(DesktopUI.button("选择完整报告副本", () -> read(true), false));
      } else {
        if (framing) rail.getChildren().add(actualButton(true));
        if (guides) rail.getChildren().add(actualButton(false));
        if (complete())
          rail.getChildren().add(DesktopUI.text("后续换卡、更新或重新打开任务时，仍需读取实际卡片重新核实。", "hint"));
      }
    }
    if (!s.enhMessage.isEmpty() && !(s.enhReportChecked && complete()))
      rail.getChildren().add(DesktopUI.text(s.enhMessage, "hint"));
    rail.getChildren()
        .addAll(
            DesktopUI.rule(),
            DesktopUI.button("管理／移除增强", this::manage, false),
            DesktopUI.button("查看开源许可", app::license, false));
    return DesktopUI.workspace("拍摄增强", photo, rail);
  }

  void draw(Canvas c) {
    double w = c.getWidth(), h = c.getHeight();
    if (w < 1 || h < 1) return;
    GraphicsContext g = c.getGraphicsContext2D();
    g.setFill(Color.web("#101010"));
    g.fillRect(0, 0, w, h);
    double iw = Math.min(w, h * 1.5), ih = iw * 2 / 3, x = (w - iw) / 2, y = (h - ih) / 2;
    g.setFill(Color.web("#424242"));
    g.fillRect(x, y, iw, ih);
    if (s.prepared != null)
      g.drawImage(new Image(new ByteArrayInputStream(s.prepared)), x, y, iw, ih);
    double top = framing ? y + (ih - iw * 24 / 65) / 2 : y,
        bottom = framing ? top + iw * 24 / 65 : y + ih;
    if (framing) {
      if (!mask.equals("仅框线")) {
        g.setFill(Color.color(0, 0, 0, mask.equals("黑色遮幅") ? 1 : .62));
        g.fillRect(x, y, iw, top - y);
        g.fillRect(x, bottom, iw, y + ih - bottom);
      }
      g.setStroke(Color.WHITE);
      g.strokeRect(x, top, iw, bottom - top);
    }
    if (guides) {
      g.setGlobalAlpha(opacity);
      g.setStroke(
          lineColor.equals("黄色")
              ? Color.YELLOW
              : lineColor.equals("黑色") ? Color.BLACK : Color.WHITE);
      g.setLineWidth(1);
      if (style.equals("中心十字") || style.equals("三分＋中心")) {
        g.strokeLine(x + iw / 2 - 15, (top + bottom) / 2, x + iw / 2 + 15, (top + bottom) / 2);
        g.strokeLine(x + iw / 2, (top + bottom) / 2 - 15, x + iw / 2, (top + bottom) / 2 + 15);
      }
      if (style.equals("对角线")) {
        g.strokeLine(x, top, x + iw, bottom);
        g.strokeLine(x + iw, top, x, bottom);
      } else if (!style.equals("中心十字")) {
        double r = style.equals("黄金分割") ? .382 : 1.0 / 3;
        for (double p : new double[] {r, 1 - r}) {
          g.strokeLine(x + iw * p, top, x + iw * p, bottom);
          g.strokeLine(x, top + (bottom - top) * p, x + iw, top + (bottom - top) * p);
        }
      }
      g.setGlobalAlpha(1);
    }
  }

  boolean complete() {
    return (!framing || s.state.getProperty("enh.actual_f", "0").equals("1"))
        && (!guides || s.state.getProperty("enh.actual_g", "0").equals("1"));
  }

  Node actualButton(boolean f) {
    String key = f ? "enh.actual_f" : "enh.actual_g",
        name = f ? "XPan 宽幅" : "构图辅助线",
        value = s.state.getProperty(key, "0");
    HBox answers = new HBox(10);
    for (int result : new int[] {1, 2}) {
      String label = result == 1 ? "正确" : "不正确";
      Button answer =
          DesktopUI.button(
              label,
              () ->
                  app.work(
                      "记录实际效果…",
                      () -> {
                        s.enhActual(f, result);
                        return null;
                      },
                      r -> app.notifyUser("实际效果已记录。")),
              false);
      answer.setAccessibleText(name + " · " + label);
      answer.setDisable(value.equals(String.valueOf(result)));
      HBox.setHgrow(answer, Priority.ALWAYS);
      answers.getChildren().add(answer);
    }
    return DesktopUI.box(
        DesktopUI.text(
            name + " · " + (value.equals("1") ? "正确" : value.equals("2") ? "不正确" : "待核对"), "body"),
        answers);
  }

  void prepare(boolean remove) {
    CameraIdentity id = s.context();
    boolean f = framing, g = guides;
    if (id == null || !id.enhancementSupported()) {
      app.notifyUser("先识别支持的相机。");
      return;
    }
    if (!remove && !f && !g) {
      app.notifyUser("至少选择一个模块。");
      return;
    }
    if (cardPath == null) {
      app.notifyUser("请插入读卡器并选择 SD 卡。");
      return;
    }
    Path selected = cardPath;
    app.work(
        "只读检查卡片与变更…",
        () -> {
          DesktopCard card = s.card(selected, true);
          EnhancementBundle b = s.resources.bundle(id);
          return new Object[] {card, EnhancementDeployment.inspect(card, b, f, g, remove)};
        },
        result -> {
          DesktopCard card = (DesktopCard) result[0];
          EnhancementDeployment.Inspection plan = (EnhancementDeployment.Inspection) result[1];
          String changes =
              remove
                  ? "移除本工具所有权记录中的文件；恢复接管前模块清单，保留其他内容。"
                  : "准备 "
                      + (f ? "XPan 宽幅 " : "")
                      + (g ? "构图辅助线" : "")
                      + "。保留已有其他模块和设置，完整读回后最后提交启动入口。";
          if (DesktopUI.review(
              app.stage,
              remove ? "核对移除增强" : "核对准备增强",
              id.model + " · " + id.firmware + "\nSD 卡：" + selected.getFileName() + "\n" + changes,
              remove ? "移除并核验" : "确认并准备"))
            app.work(
                remove ? "移除并核验…" : "准备增强文件…",
                () -> {
                  s.prepareEnh(card, id, f, g, plan);
                  return null;
                },
                r -> {
                  configuring = false;
                  s.enhMessage =
                      remove
                          ? "自有增强文件已移除。若不再使用任何 SD 脚本，请按管理中的关闭 Script 步骤操作。"
                          : "文件已完整核验。请在相机执行后读回报告。";
                  app.notifyUser(s.enhMessage);
                });
        });
  }

  void read(boolean copy) {
    Path selected = copy ? DesktopUI.chooseDirectory(app.stage, "选择完整运行报告副本") : cardPath;
    if (selected == null) {
      app.notifyUser("请选择目标 SD 卡。");
      return;
    }
    app.work(
        "读取当前文件与本轮报告…",
        () -> {
          try {
            s.readEnh(copy ? s.readCopy(selected) : s.card(selected, false), copy);
            return null;
          } catch (Exception e) {
            s.enhMessage = e.getMessage();
            throw e;
          }
        },
        r -> app.notifyUser(s.enhMessage));
  }

  void manual() {
    CameraIdentity id = s.context();
    if (id == null || !id.enhancementSupported()) {
      app.notifyUser("先识别适用相机。");
      return;
    }
    if (!framing && !guides) {
      app.notifyUser("至少选择一个模块。");
      return;
    }
    ChoiceDialog<String> choice = new ChoiceDialog<>("读取原启动日志", List.of("读取原启动日志", "全新卡，尚无 GRKit"));
    choice.initOwner(app.stage);
    choice.setTitle("准备手动包");
    choice.setHeaderText("已有 GRKit 时保留准备前 BOOT.TXT；未知启动脚本不要覆盖。");
    DesktopUI.style(choice.getDialogPane().getScene());
    choice
        .showAndWait()
        .ifPresent(
            value -> {
              try {
                byte[] baseline;
                if (value.startsWith("读取")) {
                  Path log = DesktopUI.chooseOpen(app.stage, "选择原 BOOT.TXT", "*.TXT", "*.txt");
                  if (log == null) return;
                  baseline = DesktopResources.limited(Files.newInputStream(log), 512 * 1024);
                  if (baseline.length > 512 * 1024) throw new IOException("日志过大");
                } else baseline = new byte[0];
                Path target =
                    DesktopUI.chooseSave(
                        app.stage,
                        "保存增强手动包",
                        "GRKit-1.3.5-" + EnhancementPolicy.family(id) + "-FG.zip");
                if (target == null) return;
                boolean f = framing, g = guides;
                app.work(
                    "导出增强包…",
                    () -> {
                      s.same(id);
                      Files.write(target, s.resources.bundle(id).manual(f, g));
                      s.recordEnh(id, "manual", f, g, baseline, true);
                      return null;
                    },
                    r -> {
                      configuring = false;
                      s.enhMessage = "ZIP 已保存；把 SD_ROOT 里面的内容复制到卡根目录。保存 ZIP 不代表卡片已写入或相机已执行。";
                      app.notifyUser(s.enhMessage);
                    });
              } catch (Exception e) {
                app.error(e);
              }
            });
  }

  void manage() {
    ChoiceDialog<String> d =
        new ChoiceDialog<>(
            "按当前选择增减模块", List.of("按当前选择增减模块", "移除本工具部署的增强", "恢复已暂停的增强入口", "导出关闭 SD Script 文件"));
    d.initOwner(app.stage);
    d.setTitle("管理拍摄增强");
    d.setHeaderText("只处理可核验的自有文件，保留其他模块、设置和照片。");
    DesktopUI.style(d.getDialogPane().getScene());
    d.showAndWait()
        .ifPresent(
            action -> {
              if (action.startsWith("按")) {
                configuring = true;
                app.render();
              } else if (action.startsWith("移除")) prepare(true);
              else if (action.startsWith("恢复")) resume();
              else scriptsOff();
            });
  }

  void resume() {
    if (cardPath == null) {
      app.notifyUser("请选择原任务 SD 卡。");
      return;
    }
    CameraIdentity id = s.context();
    Path root = cardPath;
    app.work(
        "核实暂停入口…",
        () -> {
          DesktopCard card = s.card(root, true);
          EnhancementBundle b = s.resources.bundle(id);
          return new Object[] {card, b, EnhancementDeployment.inspectResume(card, b)};
        },
        r -> {
          if (DesktopUI.review(app.stage, "恢复增强启动入口", "仅恢复已核验的 GRKit 暂停入口；未完成的换图任务会阻止恢复。", "确认并恢复"))
            app.work(
                "恢复并完整核验…",
                () -> {
                  DesktopCard c = (DesktopCard) r[0];
                  EnhancementBundle b = (EnhancementBundle) r[1];
                  EnhancementDeployment.Inspection inspected =
                      (EnhancementDeployment.Inspection) r[2];
                  EnhancementDeployment.compare(
                      inspected, EnhancementDeployment.inspectResume(c, b));
                  byte[] boot = s.optional(c, "GRKIT/BOOT.TXT");
                  EnhancementDeployment.resume(c, b, () -> s.same(id));
                  s.recordEnh(
                      id,
                      c.key(),
                      s.selected(c, "FRAMING.GRM"),
                      s.selected(c, "GUIDES.GRM"),
                      boot,
                      false);
                  return null;
                },
                ok -> {
                  configuring = false;
                  app.notifyUser("增强入口已恢复，请重新在相机加载后读回报告。");
                });
        });
  }

  void scriptsOff() {
    CameraIdentity id = s.context();
    if (id == null || !id.enhancementSupported()) {
      app.notifyUser("先识别适用相机。");
      return;
    }
    DesktopUI.message(
        app.stage,
        "关闭 SD Script",
        "只在不再使用任何 SD 脚本时使用。将关闭文件放到没有其他脚本的卡根目录，按住 MENU 开机，完成后移除入口文件并在相机核对。");
    Path target = DesktopUI.chooseSave(app.stage, "保存关闭文件", "GRKit-Script-Off.zip");
    if (target != null)
      app.work(
          "生成关闭文件…",
          () -> {
            EnhancementBundle b = s.resources.bundle(id);
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put(
                "SD_ROOT/" + b.entry(),
                "[SCRIPT_EN_MODE DIS]\r\n".getBytes(StandardCharsets.US_ASCII));
            files.put("SD_ROOT/DEVELOP.MOD", b.all.get("DEVELOP.MOD"));
            Files.write(target, Gr3SdSession.zip(files));
            return null;
          },
          r -> app.notifyUser("关闭文件已导出，仍需在相机执行并核对。"));
  }
}
