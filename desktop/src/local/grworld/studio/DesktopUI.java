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
  static Label text(String value, String style) {
    Label l = new Label(value);
    l.setWrapText(true);
    l.getStyleClass().add(style);
    l.setMaxWidth(Double.MAX_VALUE);
    return l;
  }

  static VBox box(Node... nodes) {
    VBox b = new VBox(14, nodes);
    b.setAlignment(Pos.TOP_LEFT);
    b.setFillWidth(true);
    return b;
  }

  static Button button(String value, Runnable action, boolean primary) {
    Button b = new Button(value);
    b.setMaxWidth(Double.MAX_VALUE);
    b.setMinHeight(42);
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
    return s;
  }

  static void style(Scene scene) {
    scene
        .getStylesheets()
        .add(Objects.requireNonNull(DesktopUI.class.getResource("/desktop.css")).toExternalForm());
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
    a.setContentText(body);
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
    if (app.session.fixtureRoot != null) {
      c.getItems().add(app.session.fixtureRoot.resolve("card"));
      c.getSelectionModel().selectFirst();
    } else c.getItems().addAll(DesktopCard.volumes());
    return c;
  }

  static BorderPane workspace(String title, Node preview, Node action) {
    VBox left = box(text(title, "section-title"), preview);
    left.setPadding(new Insets(28));
    VBox.setVgrow(preview, Priority.ALWAYS);
    ScrollPane rail = scroll(action);
    rail.setPrefWidth(360);
    rail.setMinWidth(315);
    rail.getStyleClass().add("action-rail");
    BorderPane content = new BorderPane();
    content.setCenter(left);
    content.setRight(rail);
    return content;
  }

  static VBox actions(String step, String explanation) {
    VBox v = box(text(step, "step-title"), text(explanation, "hint"));
    v.setPadding(new Insets(28, 24, 28, 24));
    return v;
  }
}
