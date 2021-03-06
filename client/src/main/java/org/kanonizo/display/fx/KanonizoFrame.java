package org.kanonizo.display.fx;

import com.scythe.instrumenter.InstrumentationProperties.Parameter;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Screen;
import javafx.util.StringConverter;
import org.kanonizo.Framework;
import org.kanonizo.algorithms.SearchAlgorithm;
import org.kanonizo.annotations.Algorithm;
import org.kanonizo.annotations.Instrumenter;
import org.kanonizo.display.Display;
import org.kanonizo.framework.objects.TestCase;
import org.kanonizo.framework.objects.TestSuite;
import org.kanonizo.gui.AlertUtils;
import org.kanonizo.gui.GuiUtils;
import org.kanonizo.gui.KanonizoFxApplication;
import org.kanonizo.listeners.TestCaseSelectionListener;
import org.kanonizo.util.Util;

public class KanonizoFrame implements Display, Initializable, TestCaseSelectionListener {

  @FXML
  private MenuItem exitButton;
  @FXML
  private ComboBox algorithmChoices;
  @FXML
  private TreeView<File> sourceTree;
  @FXML
  private TreeView<File> testTree;
  @FXML
  private ListView<File> libs;
  @FXML
  private Button selectRoot;
  @FXML
  private TextField rootFolderTextField;
  @FXML
  private GridPane paramLayout;
  @FXML
  private GridPane bottom;
  @FXML
  private ComboBox instrumenterChoices;
  @FXML
  private GridPane instParamLayout;

  private KanonizoScene scene;
  private Framework fw;
  private static final int ITEMS_PER_ROW = 2;

  @FXML
  public void exit() {
    System.exit(0);
  }


  public void selectRoot() {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setInitialDirectory(fw.getRootFolder());
    File newRoot = dc.showDialog(KanonizoFxApplication.stage);
    if (newRoot != null) {
      fw.setRootFolder(newRoot);
      rootFolderTextField.setText(newRoot.getAbsolutePath());
      sourceTree.setRoot(GuiUtils.createDynamicFileTree(newRoot));
      testTree.setRoot(GuiUtils.createDynamicFileTree(newRoot));
    }
  }

  private void addSourceListeners() {
    sourceTree.setRoot(GuiUtils.createDynamicFileTree(fw.getRootFolder()));
    sourceTree.getSelectionModel().selectedItemProperty().addListener((ov, old, nv) -> {
      if (old != nv && nv != null) {
        fw.setSourceFolder(nv.getValue());
      }
    });
    GuiUtils.setDefaultTreeNamer(sourceTree);
  }

  private void addTestListeners() {
    testTree.setRoot(GuiUtils.createDynamicFileTree(fw.getRootFolder()));
    testTree.getSelectionModel().selectedItemProperty().addListener((ov, old, nv) -> {
      if (old != nv && nv != null) {
        fw.setTestFolder(nv.getValue());
      }
    });
    GuiUtils.setDefaultTreeNamer(testTree);
  }

  private void addLibListeners() {
    ContextMenu menu = new ContextMenu();
    MenuItem addLib = new MenuItem();
    addLib.textProperty().set("Add Library");
    addLib.setOnAction((ActionEvent ev) -> {
      FileChooser fc = new FileChooser();
      fc.setInitialDirectory(fw.getRootFolder());
      fc.setSelectedExtensionFilter(new ExtensionFilter("Only jar files!", "jar"));
      List<File> jar = fc.showOpenMultipleDialog(KanonizoFxApplication.stage);
      if (jar != null) {
        for (File j : jar) {
          fw.addLibrary(j);
          libs.getItems().add(j);
        }
      }
    });

    menu.getItems().add(addLib);
    libs.addEventHandler(MouseEvent.MOUSE_CLICKED, ev -> {
      if (ev.getButton().equals(MouseButton.SECONDARY)) {
        menu.show(libs, ev.getScreenX(), ev.getScreenY());
      } else if (menu.isShowing()) {
        menu.hide();
      }
    });
    GuiUtils.setDefaultListName(libs);
  }


  private void addParams(Object alg, GridPane paramLayout) {
    List<Field> params = Arrays.asList(alg.getClass().getFields()).stream()
        .filter(f -> f.getAnnotation(Parameter.class) != null).collect(
            Collectors.toList());
    int row = 0;
    int col = -1;
    for (Field param : params) {
      if (col + 2 > ITEMS_PER_ROW * 2) {
        col = -1;
        row++;
      }
      Label paramLabel = new Label(param.getName() + ":");
      paramLabel.setAlignment(Pos.CENTER_LEFT);
      Control paramField = getParameterField(param);
      if (paramField instanceof TextField) {

        try {
          ((TextField) paramField).setText(param.get(null).toString());
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      } else if (paramField instanceof CheckBox) {

      }
      paramLayout.add(paramLabel, ++col, row, 1, 1);
      paramLayout.add(paramField, ++col, row, 1, 1);
    }
  }

  private Control getParameterField(Field param) {
    Control parameterField = null;
    Class<?> type = param.getType();
    if (type.equals(boolean.class) || type.equals(Boolean.class)) {
      parameterField = new CheckBox();
      ((CheckBox) parameterField).selectedProperty().addListener((obs, old, nw) -> {
        try {
          Util.setParameter(param, nw.toString());
          addErrors(fw.getAlgorithm());
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      });
      try {
        ((CheckBox) parameterField).setSelected((Boolean) param.get(null));
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    } else if (param.getType().equals(String.class) || param.getType().isPrimitive() || param.getType().isAssignableFrom(Number.class)) {
      parameterField = new TextField();
      ((TextField) parameterField).textProperty().addListener((obs, old, nw) -> {
        try {
          Util.setParameter(param, nw);
          addErrors(fw.getAlgorithm());
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      });
    } else if (param.getType().equals(File.class)) {

      try {
        Button control = new Button(((File) param.get(null)).getName());
        control.setOnAction(ev -> {
          FileChooser fc = new FileChooser();
          File f = fc.showOpenDialog(KanonizoFxApplication.stage);
          if (f != null) {
            try {
              Util.setParameter(param, f.getAbsolutePath());
              addErrors(fw.getAlgorithm());
            } catch (IllegalAccessException e) {
              e.printStackTrace();
            }
            control.setText(f.getName());
          }
        });
        parameterField = control;
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }
    return parameterField;
  }


  @Override
  public void initialise() {
    Application.launch(KanonizoFxApplication.class, null);
  }

  public void testCaseSelected(TestCase tc) {
    if (scene != null) {
      scene.addTestCase(tc);
    }
  }

  @Override
  public void fireTestSuiteChange(TestSuite ts) {
    if (scene != null) {
      scene.setTestCases(ts.getTestCases());
    }
  }

  @Override
  public void reportProgress(double current, double max) {
    if (scene != null) {
      scene.reportProgress(current, max);
    }
  }

  @Override
  public int ask(String question) {
    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setTitle("Confirm an Action");
    alert.setContentText(question);
    ButtonType yes = new ButtonType("Yes");
    ButtonType no = new ButtonType("No");
    alert.getButtonTypes().setAll(yes, no);
    Optional<ButtonType> result = alert.showAndWait();
    if (result.get() == yes) {
      return 0;
    } else if (result.get() == no) {
      return 1;
    }
    return -1;
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    assert sourceTree != null : "fx:id sourceTree was not injected";

    this.fw = Framework.getInstance();
    fw.addSelectionListener(this);
    fw.setDisplay(this);
    Screen main = Screen.getPrimary();
    Rectangle2D bounds = main.getVisualBounds();
    rootFolderTextField.setText(fw.getRootFolder().getAbsolutePath());
    addSourceListeners();
    addTestListeners();
    addLibListeners();
    selectRoot.setOnAction(ev -> selectRoot());
    try {
      algorithmChoices.getItems().addAll(Framework.getAvailableAlgorithms());
      algorithmChoices.setConverter(new StringConverter() {
        @Override
        public String toString(Object object) {
          return object.getClass().getAnnotation(Algorithm.class).readableName();
        }

        @Override
        public Object fromString(String string) {
          try {
            return Framework.getAvailableAlgorithms().stream().filter(
                alg -> alg.getClass().getAnnotation(Algorithm.class).readableName().equals(string))
                .findFirst().get();
          } catch (Exception e) {
            return null;
          }
        }
      });

      algorithmChoices.valueProperty().addListener((ov, t, t1) -> {
        SearchAlgorithm alg = (SearchAlgorithm) ov.getValue();
        fw.setAlgorithm(alg);
        //remove existing children
        paramLayout.getChildren().clear();
        addParams(alg, paramLayout);
        addErrors(alg);
      });
      algorithmChoices.getSelectionModel().select(fw.getAlgorithm());
      instrumenterChoices.getItems().addAll(Framework.getAvailableInstrumenters());
      instrumenterChoices.setConverter(new StringConverter() {

        @Override
        public String toString(Object object) {
          return object.getClass().getAnnotation(Instrumenter.class).readableName();
        }

        @Override
        public Object fromString(String string) {
          try {
            return Framework.getAvailableInstrumenters().stream().filter(
                inst -> inst.getClass().getAnnotation(Instrumenter.class).readableName().equals(string))
                .findFirst().get();
          } catch (Exception e) {
            return null;
          }
        }
      });
      instrumenterChoices.valueProperty().addListener((ov, t, t1) -> {
        org.kanonizo.framework.instrumentation.Instrumenter inst = (org.kanonizo.framework.instrumentation.Instrumenter) ov.getValue();
        fw.setInstrumenter(inst);
        //remove existing children
        instParamLayout.getChildren().clear();
        addParams(inst, instParamLayout);
        addErrors(inst);
      });
      instrumenterChoices.getSelectionModel().select(fw.getInstrumenter());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Set<Label> activeErrors = new HashSet<>();

  private void addErrors(Object alg) {
    if (alg instanceof SearchAlgorithm) {
      bottom.getChildren().removeAll(activeErrors);
      activeErrors.clear();
      List<String> errors = Framework.runPrerequisites((SearchAlgorithm) alg);
      int row = 2;
      for (String error : errors) {
        Label er = new Label(error);
        activeErrors.add(er);
        er.getStyleClass().add("error");
        bottom.add(er, 0, row++, 2, 1);
      }
    }
  }

  @FXML
  public void go() {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("PrioritisationScreen.fxml"));
      loader.load();
      scene = loader.getController();
      scene.setCaller(this);
      KanonizoFxApplication.stage.setScene(new Scene(loader.getRoot()));
      scene.run();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @FXML
  public void save() {

    try {
      FileChooser fc = new FileChooser();
      File saveLocation = new File(System.getProperty("user.home") + File.separator + ".kanonizo");
      if (!saveLocation.exists()) {
        saveLocation.mkdir();
      }
      fc.setInitialDirectory(saveLocation);
      File toWrite = fc.showSaveDialog(KanonizoFxApplication.stage);
      new Thread(() -> {
        try {
          fw.write(toWrite);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }).start();

    } catch (Exception e) {
      AlertUtils.alert(AlertType.ERROR, e.getClass().getName(), e.getMessage());
    }
  }

  @FXML
  public void load() {
    try {
      FileChooser fc = new FileChooser();
      File loadLocation = new File(System.getProperty("user.home") + File.separator + ".kanonizo");
      if (!loadLocation.exists()) {
        loadLocation.mkdir();
      }
      fc.setInitialDirectory(loadLocation);
      File toRead = fc.showOpenDialog(KanonizoFxApplication.stage);
      if(toRead != null) {
        new Thread(() -> {
          try {
            Framework read = fw.read(toRead);
            this.fw.setSourceFolder(read.getSourceFolder());
            this.fw.setRootFolder(read.getRootFolder());
            this.fw.setTestFolder(read.getTestFolder());
            this.fw.setAlgorithm(read.getAlgorithm());
            this.fw.setInstrumenter(read.getInstrumenter());
            read.getLibraries().forEach(lib -> this.fw.addLibrary(lib));
            Platform.runLater(() -> {
              File root = fw.getRootFolder();
              rootFolderTextField.setText(root.getAbsolutePath());
              sourceTree.setRoot(GuiUtils.createDynamicFileTree(root));
              sourceTree.scrollTo(findIndexOfChild(fw.getSourceFolder(), sourceTree));
              testTree.setRoot(GuiUtils.createDynamicFileTree(root));
              testTree.scrollTo(findIndexOfChild(fw.getTestFolder(), testTree));
              libs.getItems().addAll(fw.getLibraries());
              algorithmChoices.getSelectionModel().select(fw.getAlgorithm());
              instrumenterChoices.getSelectionModel().select(fw.getInstrumenter());
            });
          } catch (Exception e) {
            e.printStackTrace();
          }
        }).start();
      }

    } catch (Exception e) {
      AlertUtils.alert(AlertType.ERROR, e.getClass().getName(), e.getMessage());
    }

  }

  private int findIndexOfChild(File folder, TreeView<File> view) {
    ObservableList<Node> children = view.getChildrenUnmodifiable();
    for (Node child : children) {

    }
    return -1;
  }
}
