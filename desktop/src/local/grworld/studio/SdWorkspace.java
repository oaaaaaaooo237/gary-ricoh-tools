package local.grworld.studio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

final class SdWorkspace {
  final DesktopApp app;
  final DesktopSession s;
  Path cardPath;
  boolean editionConfirmed;

  SdWorkspace(DesktopApp app) {
    this.app = app;
    s = app.session;
  }

  Node view() {
    if (s.job == null) return prepareView();
    Gr3SdSession j = s.job;
    SdReconciler.Result r = s.sdResult;
    String step, explanation;
    int action = 0;
    boolean cleaned =
        j.id.equals(s.state.getProperty("sd.cleaned")) && r != null && r.cleaned && !s.sdCopy;
    int manual = 0;
    for (int p = 1; p <= 3; p++)
      if ((j.id + "/" + p).equals(s.state.getProperty("sd.manual"))) manual = p;
    if (!s.sdVerified) {
      step = "核实 SD 实际进度";
      explanation = "重新读取目标卡的启动文件、任务记录和完整读回。历史完成标记不能代替当前证据。";
    } else if (j.installed || j.restored) {
      if (j.display == 0) {
        step = "核对实际关机画面";
        explanation = "本轮完整目标读回已核实。请关机查看实际画面，再记录显示结果。";
      } else if (!cleaned) {
        step = "清理本次启动任务";
        explanation = "移除经核验的本任务启动文件；若暂停过本工具的增强，将恢复其入口。";
        action = 4;
      } else {
        step = "核对脚本状态";
        explanation =
            j.id.equals(s.state.getProperty("sd.enhanced"))
                ? "已清理换图入口并恢复增强。请重新开机核对增强；继续使用增强时保持 Script 开启。"
                : "本任务入口已清理。请在相机关闭 Script，并确认相机正常开关机。";
        action = 5;
      }
    } else if (!j.staged) {
      if (r != null && r.preparedPhase == 1 || manual == 1) {
        step = "在相机执行暂存";
        explanation = "文件已准备，尚未证明机内执行。安全弹出卡并放回相机，按任务说明执行；完成后把卡接回电脑读取本轮全部结果。";
      } else {
        step = "准备暂存阶段";
        explanation = "先把候选图暂存至相机内部，并取得当前关机图备份与完整读回；校验通过才开放安装。";
        action = 1;
      }
    } else if (r != null && r.preparedPhase == 2 || manual == 2) {
      step = "在相机执行安装";
      explanation = "安装卡文件已准备。相机执行后，把卡接回电脑，完整核对本轮目标图片和完成标记。";
    } else if (r != null && r.preparedPhase == 3 || manual == 3) {
      step = "在相机执行旧图恢复";
      explanation = "旧图恢复文件已准备；相机执行后仍须完整读回，之后核对实际显示。";
    } else {
      step = "准备安装阶段";
      explanation = "暂存、备份与操作前目标已完整核验。下一步把内部候选图安装到目标，再完整读回。";
      action = 2;
    }
    if (action == 5 && j.id.equals(s.state.getProperty("sd.script_checked"))) {
      step = j.display == 1 ? "本轮流程完成" : "本轮结束 · 显示未正确变化";
      explanation = "文件执行证据、实际显示结果和启动入口清理状态均已记录。新一轮换图请建立新任务。";
    }
    VBox v = DesktopUI.actions(step, explanation);
    v.getChildren()
        .add(
            DesktopUI.text(
                j.profile.label + " · 固件1.70\n任务 " + j.id + " · " + j.candidate.length + " 字节",
                "hint"));
    ComboBox<Path> cards = cardBox();
    if (!s.sdVerified) {
      v.getChildren()
          .addAll(
              cards,
              DesktopUI.button("读取 SD 卡，核实任务进度", () -> read(false), true),
              DesktopUI.button("读取完整读回副本", () -> read(true), false));
    } else if (action == 1 || action == 2) {
      int phase = action;
      v.getChildren()
          .addAll(
              cards,
              DesktopUI.button("准备 SD 卡：" + (phase == 1 ? "暂存" : "安装"), () -> deploy(phase), true),
              DesktopUI.button(
                  "手动导出" + (phase == 1 ? "暂存" : "安装") + " ZIP", () -> exportPhase(phase), false));
    } else if (action == 4) {
      v.getChildren().addAll(cards, DesktopUI.button("移除本任务启动文件", this::cleanup, true));
    } else if (action == 5) {
      if (!j.id.equals(s.state.getProperty("sd.script_checked"))) {
        CheckBox checked =
            new CheckBox(
                j.id.equals(s.state.getProperty("sd.enhanced"))
                    ? "已重新开机核对增强，Script 保持开启"
                    : "已在相机关闭 Script，并核对正常开关机");
        checked.setWrapText(true);
        checked.setOnAction(
            e -> {
              if (checked.isSelected())
                app.work(
                    "记录脚本状态…",
                    () -> {
                      s.state.setProperty("sd.script_checked", j.id);
                      s.persist();
                      return null;
                    },
                    ok -> app.notifyUser("本轮流程已结束。"));
            });
        v.getChildren().add(checked);
      }
    } else if (s.sdVerified && (j.installed || j.restored) && j.display == 0) {
      v.getChildren()
          .addAll(
              DesktopUI.button("显示正确", () -> actual(1), true),
              DesktopUI.button("显示不正确", () -> actual(2), false),
              DesktopUI.button("暂未核对", () -> app.notifyUser("保持实际显示待核对。"), false));
    } else if (s.sdVerified) {
      v.getChildren()
          .addAll(
              cards,
              DesktopUI.button("读取相机执行结果", () -> read(false), true),
              DesktopUI.button("选择完整读回副本", () -> read(true), false),
              DesktopUI.button(
                  "查看本阶段相机操作说明",
                  () ->
                      DesktopUI.longText(
                          app.stage,
                          "相机操作说明",
                          j.instructions(
                              s.sdResult != null && s.sdResult.preparedPhase == 3
                                  ? 3
                                  : j.staged ? 2 : 1)),
                  false));
    }
    if (!s.sdMessage.isEmpty()) v.getChildren().add(DesktopUI.text(s.sdMessage, "hint"));
    v.getChildren().addAll(DesktopUI.rule(), DesktopUI.button("任务、旧图恢复与异常清理", this::manage, false));
    return v;
  }

  VBox prepareView() {
    VBox v = DesktopUI.actions("准备 SD 关机图任务", "此机型使用 SD 分阶段方法。先按机身版本处理候选图，再核实目标卡片。文件准备不代表相机已经执行。");
    ComboBox<Gr3Profile> editions = new ComboBox<>();
    editions.getItems().addAll(Gr3Profile.values());
    editions.setConverter(
        new javafx.util.StringConverter<>() {
          public String toString(Gr3Profile p) {
            return p == null ? "" : p.label;
          }

          public Gr3Profile fromString(String s) {
            return null;
          }
        });
    editions.setValue(s.profile());
    boolean ambiguous = s.context() != null && s.context().model.equals("RICOH GR IIIx");
    editions.setDisable(!ambiguous);
    editions.setMaxWidth(Double.MAX_VALUE);
    CheckBox confirm = new CheckBox("已核对本次 SD 任务对应的机身版本");
    confirm.setWrapText(true);
    confirm.setSelected(editionConfirmed);
    confirm.setOnAction(e -> editionConfirmed = confirm.isSelected());
    Button prepare =
        DesktopUI.button(
            "处理当前图片",
            () -> {
              if (!confirm.isSelected()) {
                app.notifyUser("先核对机身版本。");
                return;
              }
              app.work(
                  "编码该机型的精确长度 JPEG…",
                  () -> {
                    s.newSd(editions.getValue(), false);
                    return null;
                  },
                  r -> app.notifyUser("候选图片已准备，接下来读取实际卡片核实进度。"));
            },
            true);
    prepare.setDisable(s.prepared == null);
    Button factory =
        DesktopUI.button(
            "准备原厂图片",
            () -> {
              if (!confirm.isSelected()) {
                app.notifyUser("先核对机身版本。");
                return;
              }
              app.work(
                  "准备对应原厂图片…",
                  () -> {
                    s.newSd(editions.getValue(), true);
                    return null;
                  },
                  r -> app.notifyUser("原厂恢复本地任务已准备。"));
            },
            false);
    v.getChildren()
        .addAll(
            editions,
            confirm,
            DesktopUI.text("普通／HDF 为7264字节，Urban为107902字节；复杂照片可能需要降低细节。", "hint"),
            prepare,
            factory,
            DesktopUI.rule(),
            DesktopUI.button("导入或打开已有任务", this::history, false));
    return v;
  }

  ComboBox<Path> cardBox() {
    ComboBox<Path> cards = DesktopUI.cards(app);
    if (cardPath != null && cards.getItems().contains(cardPath)) cards.setValue(cardPath);
    if (cardPath == null) cardPath = cards.getValue();
    cards.setOnAction(e -> cardPath = cards.getValue());
    return cards;
  }

  void read(boolean copy) {
    Path selected = copy ? DesktopUI.chooseDirectory(app.stage, "选择本轮完整读回文件夹") : cardPath;
    if (selected == null) {
      app.notifyUser("请选择目标卡片。");
      return;
    }
    app.work(
        "核实卡内实际进度…",
        () -> {
          try {
            s.reconcile(copy ? s.readCopy(selected) : s.card(selected, false), copy);
            return null;
          } catch (Exception e) {
            s.sdVerified = false;
            s.sdMessage = e.getMessage();
            throw e;
          }
        },
        r -> app.notifyUser(s.sdMessage));
  }

  void deploy(int phase) {
    if (cardPath == null) {
      app.notifyUser("请插入读卡器并选择 SD 卡。");
      return;
    }
    Path selected = cardPath;
    app.work(
        "只读检查本阶段变更…",
        () -> {
          DesktopCard card = s.card(selected, true);
          return new Object[] {card, s.inspectSd(card, phase)};
        },
        result -> {
          DesktopCard card = (DesktopCard) result[0];
          DesktopSession.SdReview review = (DesktopSession.SdReview) result[1];
          String extra = review.pause() ? "\n将暂时暂停已核验的增强启动入口，结束换图后恢复。" : "";
          if (DesktopUI.review(
              app.stage,
              "核对 SD " + review.plan().title(),
              s.context().model
                  + " · 固件1.70\n任务 "
                  + s.job.id
                  + "\n卡片："
                  + selected
                  + "\n本步只准备 "
                  + review.plan().title()
                  + " 文件并完整读回。相机执行结果仍需另行核验。"
                  + extra,
              "确认并准备"))
            app.work(
                "写卡并完整核验…",
                () -> {
                  s.deploySd(card, review);
                  return null;
                },
                ok -> app.notifyUser("本阶段 SD 文件已准备。安全弹出，再按说明在相机执行。"));
        });
  }

  void exportPhase(int phase) {
    try {
      byte[] zip = s.sdPackage(phase);
      Path target =
          DesktopUI.chooseSave(
              app.stage, "保存本阶段 SD 包", "GR3x-" + s.job.id + "-phase" + phase + ".zip");
      if (target == null) return;
      app.work(
          "保存本阶段包…",
          () -> {
            Files.write(target, zip);
            s.manualSdSaved(phase);
            return null;
          },
          r -> app.notifyUser("ZIP 已保存。把 card 内的内容复制到卡根目录；保存不代表实际卡已准备或相机已执行。"));
    } catch (Exception e) {
      app.error(e);
    }
  }

  void actual(int result) {
    app.work(
        "记录实际关机显示…",
        () -> {
          s.sdActual(result);
          return null;
        },
        r -> app.notifyUser("实际显示已记录，接下来清理本轮启动任务。"));
  }

  void cleanup() {
    if (cardPath == null) {
      app.notifyUser("清理必须选择实际目标卡。");
      return;
    }
    Path selected = cardPath;
    if (!DesktopUI.review(
        app.stage, "清理本次启动任务", "只移除经过任务归属和完整哈希核实的启动文件。保留照片、备份和读回结果；已知暂停增强将恢复。", "确认并清理")) return;
    app.work(
        "核实并清理启动入口…",
        () -> {
          s.cleanup(s.card(selected, true));
          return null;
        },
        r -> app.notifyUser("入口已清理，请按当前步骤核对 Script 或恢复后的增强。"));
  }

  void manage() {
    List<String> options = new ArrayList<>(List.of("导出当前任务记录", "导入或打开任务", "新建换图任务", "只清理本次启动任务"));
    if (s.job != null && s.job.canRestore() && !s.job.restored) options.add("恢复本轮旧图");
    ChoiceDialog<String> d = new ChoiceDialog<>(options.get(0), options);
    d.initOwner(app.stage);
    d.setTitle("任务与恢复");
    d.setHeaderText("中断先读卡核实，不按本地完成标记重复运行。");
    DesktopUI.style(d.getDialogPane().getScene());
    d.showAndWait()
        .ifPresent(
            action -> {
              if (action.startsWith("导出")) {
                Path p = DesktopUI.chooseSave(app.stage, "保存任务记录", "GR3x-" + s.job.id + ".gr3sd");
                if (p != null)
                  app.work(
                      "保存任务…",
                      () -> {
                        Files.write(p, s.job.save());
                        return null;
                      },
                      r -> app.notifyUser("任务已导出。"));
              } else if (action.startsWith("导入")) history();
              else if (action.startsWith("新建")) {
                if (DesktopUI.review(
                    app.stage, "建立新任务", "当前任务将保留为历史记录。新一轮仍需重新核实目标卡和机内进度。", "保留历史并新建"))
                  app.work(
                      "保留任务历史…",
                      () -> {
                        s.saveJob();
                        s.store.write("sd-" + s.job.id + ".gr3sd", s.job.save());
                        s.job = null;
                        s.sdResult = null;
                        s.sdVerified = false;
                        s.state.remove("sd.active");
                        s.persist();
                        return null;
                      },
                      r -> {
                        editionConfirmed = false;
                        app.notifyUser("原任务已保留，可以选择新图片。");
                      });
              } else if (action.startsWith("只清理")) cleanup();
              else deploy(3);
            });
  }

  void history() {
    ChoiceDialog<String> choice = new ChoiceDialog<>("从文件导入", List.of("从文件导入", "打开本机历史任务"));
    choice.initOwner(app.stage);
    choice.setTitle("打开 SD 任务");
    DesktopUI.style(choice.getDialogPane().getScene());
    choice
        .showAndWait()
        .ifPresent(
            action -> {
              try {
                if (action.startsWith("从")) {
                  Path path = DesktopUI.chooseOpen(app.stage, "选择任务记录", "*.gr3sd");
                  if (path != null)
                    load(DesktopResources.limited(Files.newInputStream(path), 2 * 1024 * 1024));
                } else {
                  List<String> files =
                      s.store.list("sd-").stream().filter(f -> f.endsWith(".gr3sd")).toList();
                  if (files.isEmpty()) {
                    app.notifyUser("尚无本机历史任务。");
                    return;
                  }
                  ChoiceDialog<String> d = new ChoiceDialog<>(files.get(0), files);
                  d.initOwner(app.stage);
                  d.setTitle("历史任务");
                  DesktopUI.style(d.getDialogPane().getScene());
                  d.showAndWait()
                      .ifPresent(
                          file -> {
                            try {
                              load(s.store.read(file));
                            } catch (Exception e) {
                              app.error(e);
                            }
                          });
                }
              } catch (Exception e) {
                app.error(e);
              }
            });
  }

  void load(byte[] archive) {
    app.work(
        "校验并载入任务…",
        () -> {
          s.importSd(archive);
          return null;
        },
        r -> app.notifyUser("任务仅作历史，先读取实际卡片核实进度。"));
  }
}
