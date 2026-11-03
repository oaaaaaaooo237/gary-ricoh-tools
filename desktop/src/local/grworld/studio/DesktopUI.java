package local.grworld.studio;

import java.nio.file.*;
import java.util.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;

final class DesktopUI {
  /** A full-width native toggle, with GR WORLD-style trailing selection feedback. */
  static ToggleButton choiceRow(
      String title, String detail, boolean selected, boolean switchStyle) {
    ToggleButton row = new ToggleButton();
    row.getStyleClass().add("choice-row");
    row.setSelected(selected);
    row.setMaxWidth(Double.MAX_VALUE);
    row.setMinWidth(0);
    row.setPrefHeight(detail == null || detail.isBlank() ? 46 : 56);
    row.setMinHeight(Region.USE_PREF_SIZE);
    row.setMaxHeight(Region.USE_PREF_SIZE);
    row.setAccessibleText(title);
    row.setAccessibleHelp("可多选；按空格或点击整行切换选择。这里只选择准备内容。");
    Label heading = text(title, "choice-title");
    heading.setMinHeight(Region.USE_PREF_SIZE);
    VBox copy = new VBox(3, heading);
    if (detail != null && !detail.isBlank()) {
      Label description = text(detail, "choice-detail");
      description.setMinHeight(Region.USE_PREF_SIZE);
      copy.getChildren().add(description);
    }
    copy.setMinWidth(0);
    copy.setMaxHeight(Region.USE_PREF_SIZE);
    HBox.setHgrow(copy, Priority.ALWAYS);
    StackPane indicator = new StackPane();
    indicator.getStyleClass().add(switchStyle ? "choice-switch" : "choice-check");
    indicator.setMinSize(switchStyle ? 36 : 21, 21);
    indicator.setMaxSize(switchStyle ? 36 : 21, 21);
    if (switchStyle) {
      javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(7.5);
      dot.getStyleClass().add("choice-knob");
      dot.translateXProperty()
          .bind(
              javafx.beans.binding.Bindings.when(row.selectedProperty()).then(7.0).otherwise(-7.0));
      indicator.getChildren().add(dot);
    } else {
      javafx.scene.shape.Polyline check = new javafx.scene.shape.Polyline(0, 5, 4, 9, 11, 0);
      check.getStyleClass().add("choice-tick");
      check.visibleProperty().bind(row.selectedProperty());
      indicator.getChildren().add(check);
    }
    HBox trailing = new HBox(8);
    trailing.setAlignment(Pos.CENTER_RIGHT);
    trailing.getChildren().add(indicator);
    HBox content = new HBox(12, copy, trailing);
    content.setAlignment(Pos.CENTER_LEFT);
    content.setMaxWidth(Double.MAX_VALUE);
    content
        .prefWidthProperty()
        .bind(javafx.beans.binding.Bindings.max(180, row.widthProperty().subtract(24)));
    content.setMaxHeight(Region.USE_PREF_SIZE);
    content.setMouseTransparent(true);
    row.setGraphic(content);
    row.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    return row;
  }

  static Label text(String value, String style) {
    Label l = new Label(value);
    l.setWrapText(true);
    l.getStyleClass().add(style);
    l.setMaxWidth(Double.MAX_VALUE);
    return l;
  }

  static VBox box(Node... nodes) {
    VBox b = new VBox(12, nodes);
    b.setAlignment(Pos.TOP_LEFT);
    b.setFillWidth(true);
    return b;
  }

  static Button button(String value, Runnable action, boolean primary) {
    Button b = new Button(value);
    b.setMaxWidth(Double.MAX_VALUE);
    b.setMinHeight(38);
    b.getStyleClass().add(primary ? "primary" : "secondary");
    b.setOnAction(e -> action.run());
    return b;
  }

  static Separator rule() {
    return new Separator();
  }

  static ScrollPane scroll(Node node) {
    ScrollPane s = new ScrollPane(node);
    s.setFitToWidth(true);
    s.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    s.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    return s;
  }

  static StackPane featureArt(String id) {
    String name =
        id.equals("shutdown-image") ? "shutdown-screen-v2.png" : "shooting-enhancement-v2.png";
    ImageView art =
        new ImageView(
            new Image(
                Objects.requireNonNull(DesktopUI.class.getResource("/feature-icons/" + name))
                    .toExternalForm()));
    art.setPreserveRatio(true);
    art.setSmooth(true);
    art.setViewport(new Rectangle2D(90, 290, 1080, 740));
    StackPane well = new StackPane(art);
    art.fitWidthProperty()
        .bind(javafx.beans.binding.Bindings.min(440, well.widthProperty().subtract(24)));
    art.fitHeightProperty().bind(well.heightProperty().subtract(12));
    well.getStyleClass().add("feature-art");
    well.setMinHeight(200);
    well.setPrefHeight(220);
    well.setMaxWidth(Double.MAX_VALUE);
    well.setMouseTransparent(true);
    return well;
  }

  static void style(Scene scene) {
    scene
        .getStylesheets()
        .add(Objects.requireNonNull(DesktopUI.class.getResource("/desktop.css")).toExternalForm());
    if (scene.getWindow() != null) constrainDialog(scene.getWindow());
    else
      scene
          .windowProperty()
          .addListener(
              (o, a, window) -> {
                if (window != null) constrainDialog(window);
              });
  }

  static void constrainDialog(Window window) {
    if (!(window instanceof Stage dialog) || dialog.getOwner() == null) return;
    dialog.addEventHandler(
        WindowEvent.WINDOW_SHOWN,
        event -> {
          Window owner = dialog.getOwner();
          Rectangle2D bounds =
              Screen.getScreensForRectangle(
                      owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                  .stream()
                  .findFirst()
                  .orElse(Screen.getPrimary())
                  .getVisualBounds();
          dialog.setWidth(Math.min(dialog.getWidth(), bounds.getWidth() - 24));
          dialog.setHeight(Math.min(dialog.getHeight(), bounds.getHeight() - 24));
          dialog.setX(
              Math.max(
                  bounds.getMinX() + 12,
                  Math.min(dialog.getX(), bounds.getMaxX() - dialog.getWidth() - 12)));
          dialog.setY(
              Math.max(
                  bounds.getMinY() + 12,
                  Math.min(dialog.getY(), bounds.getMaxY() - dialog.getHeight() - 12)));
        });
  }

  static boolean review(Window owner, String title, String explanation, String action) {
    Dialog<Boolean> d = new Dialog<>();
    d.initOwner(owner);
    d.setTitle(title);
    CheckBox confirm = new CheckBox("确认这是本次目标卡，并按上述变更操作");
    confirm.setWrapText(true);
    VBox body = box(text(explanation, "body"), confirm);
    body.setPadding(new Insets(20));
    body.setPrefWidth(500);
    d.getDialogPane().setContent(scroll(body));
    ButtonType yes = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
    d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, yes);
    d.getDialogPane().lookupButton(yes).disableProperty().bind(confirm.selectedProperty().not());
    d.setResultConverter(t -> t == yes);
    style(d.getDialogPane().getScene());
    return d.showAndWait().orElse(false);
  }

  static void message(Window owner, String title, String body) {
    Alert a = new Alert(Alert.AlertType.INFORMATION);
    a.initOwner(owner);
    a.setTitle(title);
    a.setHeaderText(title);
    Label content = text(body, "body");
    content.setMinHeight(Region.USE_PREF_SIZE);
    content.setPrefWidth(480);
    a.getDialogPane().setContent(content);
    a.getDialogPane().setPrefWidth(520);
    style(a.getDialogPane().getScene());
    a.showAndWait();
  }

  static void longText(Window owner, String title, String body) {
    Dialog<Void> d = new Dialog<>();
    d.initOwner(owner);
    d.setTitle(title);
    TextArea area = new TextArea(body);
    area.setEditable(false);
    area.setWrapText(true);
    area.setPrefSize(650, 430);
    d.getDialogPane().setContent(area);
    d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    style(d.getDialogPane().getScene());
    d.showAndWait();
  }

  static Optional<String> actionChoice(
      Window owner, String title, String note, List<String> actions) {
    Dialog<String> d = new Dialog<>();
    d.initOwner(owner);
    d.setTitle(title);
    VBox body = box(text(note, "hint"));
    body.setPadding(new Insets(24));
    body.setPrefWidth(460);
    for (String action : actions)
      body.getChildren()
          .add(
              button(
                  action,
                  () -> {
                    d.setResult(action);
                    d.close();
                  },
                  false));
    ScrollPane scroll = scroll(body);
    scroll.setPrefViewportHeight(Math.min(460, 90 + actions.size() * 60));
    d.getDialogPane().setContent(scroll);
    d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
    style(d.getDialogPane().getScene());
    return d.showAndWait();
  }

  static Path chooseOpen(Window owner, String title, String... patterns) {
    FileChooser f = new FileChooser();
    f.setTitle(title);
    if (patterns.length > 0)
      f.getExtensionFilters().add(new FileChooser.ExtensionFilter("支持的文件", patterns));
    java.io.File selected = f.showOpenDialog(owner);
    return selected == null ? null : selected.toPath();
  }

  static Path chooseSave(Window owner, String title, String name) {
    FileChooser f = new FileChooser();
    f.setTitle(title);
    f.setInitialFileName(name);
    java.io.File selected = f.showSaveDialog(owner);
    return selected == null ? null : selected.toPath();
  }

  static Path chooseDirectory(Window owner, String title) {
    DirectoryChooser d = new DirectoryChooser();
    d.setTitle(title);
    java.io.File f = d.showDialog(owner);
    return f == null ? null : f.toPath();
  }

  static StackPane photo(byte[] jpeg, String empty) {
    StackPane p = new StackPane();
    p.getStyleClass().add("photo-well");
    p.setMinSize(260, 180);
    p.setPrefSize(650, 430);
    if (jpeg == null) p.getChildren().add(text(empty, "empty"));
    else {
      ImageView v = new ImageView(new Image(new java.io.ByteArrayInputStream(jpeg)));
      v.setPreserveRatio(true);
      v.fitWidthProperty().bind(p.widthProperty().subtract(24));
      v.fitHeightProperty().bind(p.heightProperty().subtract(24));
      p.getChildren().add(v);
    }
    return p;
  }

  static ComboBox<Path> cards(DesktopApp app) {
    ComboBox<Path> c = new ComboBox<>();
    c.setMaxWidth(Double.MAX_VALUE);
    c.setPromptText("选择读卡器中的 SD 卡");
    ListCell<Path> display =
        new ListCell<>() {
          @Override
          protected void updateItem(Path path, boolean empty) {
            super.updateItem(path, empty);
            setText(path == null ? c.getPromptText() : path.toString());
            setGraphic(null);
          }
        };
    c.setButtonCell(display);
    c.promptTextProperty()
        .addListener(
            (o, a, prompt) -> {
              if (c.getValue() == null) display.setText(prompt);
            });
    c.setTooltip(new Tooltip("插拔读卡器后展开此列表，程序会重新检测存储卡。"));
    refreshCards(c, app);
    if (app.session.fixtureRoot != null && !c.getItems().isEmpty())
      c.getSelectionModel().selectFirst();
    c.setOnShowing(e -> refreshCards(c, app));
    return c;
  }

  static void refreshCards(ComboBox<Path> cards, DesktopApp app) {
    Path selected = cards.getValue();
    List<Path> current;
    try {
      current = app.cards().volumes().stream().map(v -> Path.of(v.id())).toList();
    } catch (Exception e) {
      cards.getItems().clear();
      cards.setValue(null);
      cards.setPromptText("无法读取存储卡，重新展开重试");
      return;
    }
    if (!cards.getItems().equals(current)) {
      cards.getItems().setAll(current);
      cards.setValue(current.contains(selected) ? selected : null);
    }
    cards.setPromptText(current.isEmpty() ? "未检测到存储卡，插卡后展开重试" : "选择读卡器中的 SD 卡");
  }

  static BorderPane workspace(String title, Node preview, Node action) {
    VBox left = box(text(title, "section-title"), preview);
    left.setPadding(new Insets(22));
    VBox.setVgrow(preview, Priority.ALWAYS);
    ScrollPane rail = scroll(action);
    rail.setPrefWidth(340);
    rail.setMinWidth(300);
    rail.getStyleClass().add("action-rail");
    BorderPane content = new BorderPane();
    content.setCenter(left);
    content.setRight(rail);
    return content;
  }

  static VBox actions(String step, String explanation) {
    VBox v = box(text(step, "step-title"), text(explanation, "hint"));
    v.setPadding(new Insets(22, 20, 22, 20));
    return v;
  }
}
