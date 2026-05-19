package calc;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CalculatorApp extends Application {

    private static final double WIDTH = 360;
    private static final double HEIGHT = 600;
    private static final double SHADOW_PAD = 10;
    private static final int HISTORY_MAX = 50;
    private static final Path APP_DIR = resolveAppDir();
    private static final Path HISTORY_PATH = APP_DIR.resolve("history.txt");
    private static final Path VARS_PATH = APP_DIR.resolve("variables.txt");
    private static final Path SLOTS_PATH = APP_DIR.resolve("slots.txt");
    private static final Path SETTINGS_PATH = APP_DIR.resolve("settings.txt");
    private static final int N_SLOTS = 5;
    private static final String DEFAULT_ACCENT = "#FF7043";
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    enum Mode {
        STANDARD("Standard"),
        SCIENTIFIC("Scientifica"),
        PROGRAMMER("Programmatore"),
        DATE("Calcolo date"),
        GRAPHING("Grafici");
        final String label;
        Mode(String l) { this.label = l; }
    }

    // ===== State =====
    private final StringBuilder expression = new StringBuilder();
    private boolean radians;
    private boolean second;
    private Mode mode = Mode.SCIENTIFIC;
    private int base = 10;
    private String lastAnswer = "0";
    private BigInteger lastAnswerProg = BigInteger.ZERO;
    private boolean justEvaluated;
    private final List<String[]> history = new ArrayList<>();
    private final Map<String, Double> variables = new LinkedHashMap<>();
    private final List<Runnable> varListeners = new ArrayList<>();
    private final double[] slots = new double[N_SLOTS];
    private final boolean[] slotSet = new boolean[N_SLOTS];

    enum Notation { AUTO, SCIENTIFIC, ENGINEERING }
    private Notation notation = Notation.AUTO;
    private int decimalPlaces = 6;
    private boolean lightTheme = false;
    private boolean autoTheme = false;
    private String accentHex = DEFAULT_ACCENT;
    private Timeline themePoller;

    enum MemMode { NONE, MC, MR, ADD, SUB }
    private MemMode pendingMemOp = MemMode.NONE;

    // ===== UI =====
    private final Label exprLabel = new Label("");
    private final Label resultLabel = new Label("0");
    private final Label indicatorsLabel = new Label("");
    private final ToggleButton degRadToggle = new ToggleButton("DEG");
    private final ToggleButton secondToggle = new ToggleButton("2nd");
    private final Button modeBtn = new Button("☰");
    private final Button histBtn = new Button("⏱");
    private final Button saveBtn = new Button("💾");
    private GridPane grid;
    private BorderPane mainPane;
    private VBox historyPanel;
    private Node toolkitPanel;
    private ListView<String> historyList;
    private final Map<String, Button> sciButtons = new HashMap<>();
    private final Map<String, Button> progDigitButtons = new HashMap<>();
    private final Map<String, Button> baseButtons = new HashMap<>();
    private final Button[] slotBtns = new Button[N_SLOTS];
    private final Label[] slotValLabels = new Label[N_SLOTS];
    private HBox slotsRow;
    private HBox memoryCmds;
    private VBox memoryRevealBox;
    private Label memOpHint;
    private VBox calcChrome;
    private VBox display;
    private Toolkit toolkit;
    private Node modePanel;
    private Node settingsPanel;
    private Node aboutPanel;
    private Node dateView;
    private Node graphView;
    private final ToggleButton pinBtn = new ToggleButton("📌");
    private final Map<Mode, Button> modeRows = new EnumMap<>(Mode.class);
    private StackPane contentStack;

    @Override
    public void start(Stage stage) {
        loadHistory();
        loadVariables();
        loadSlots();
        loadSettings();

        mainPane = new BorderPane();
        mainPane.getStyleClass().add("root");
        mainPane.setPadding(new Insets(12));
        mainPane.setTop(buildTopArea());
        rebuildGrid();

        historyPanel = buildHistoryPanel();
        historyPanel.setVisible(false);

        toolkit = new Toolkit(buildBridge(), this::toggleToolkit, this::backFromToolkitToMenu);
        toolkitPanel = toolkit.buildPanel();
        toolkitPanel.setVisible(false);

        modePanel = buildModePanel();
        modePanel.setVisible(false);

        settingsPanel = buildSettingsPanel();
        settingsPanel.setVisible(false);

        aboutPanel = buildAboutPanel();
        aboutPanel.setVisible(false);

        contentStack = new StackPane(mainPane, historyPanel, toolkitPanel, modePanel, settingsPanel, aboutPanel);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        HBox titleBar = buildTitleBar(stage);

        VBox windowRoot = new VBox(titleBar, contentStack);
        windowRoot.getStyleClass().add("window-root");

        StackPane sceneRoot = new StackPane(windowRoot);
        // alpha 0.004 is visually imperceptible but prevents Windows from treating
        // these pixels as click-through, so corner-resize hit zones work reliably.
        sceneRoot.setStyle("-fx-background-color: rgba(0,0,0,0.004);");
        sceneRoot.setPadding(new Insets(SHADOW_PAD));

        stage.maximizedProperty().addListener((o, ov, nv) -> {
            sceneRoot.setPadding(nv ? Insets.EMPTY : new Insets(SHADOW_PAD));
            if (nv) windowRoot.getStyleClass().add("maximized");
            else windowRoot.getStyleClass().remove("maximized");
        });

        Scene scene = new Scene(sceneRoot, WIDTH + 2 * SHADOW_PAD, HEIGHT + 2 * SHADOW_PAD);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        scene.addEventFilter(KeyEvent.KEY_TYPED, this::handleKeyTyped);

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Calculator");
        stage.setScene(scene);
        stage.setMinWidth(280 + 2 * SHADOW_PAD);
        stage.setMinHeight(440 + 2 * SHADOW_PAD);
        stage.setOnCloseRequest(e -> { saveHistory(); saveVariables(); saveSlots(); saveSettings(); });
        stage.setOpacity(0.92);
        stage.getIcons().addAll(
            buildAppIcon(16),  buildAppIcon(24),  buildAppIcon(32),
            buildAppIcon(48),  buildAppIcon(64),  buildAppIcon(128), buildAppIcon(256)
        );
        stage.show();

        pinBtn.setOnAction(e -> {
            boolean pinned = pinBtn.isSelected();
            stage.setAlwaysOnTop(pinned);
            stage.setOpacity(pinned ? 1.0 : 0.92);
        });
        installResizeHandlers(stage, windowRoot);

        // Apply persisted settings (after scene is attached)
        if (autoTheme) {
            lightTheme = detectSystemThemeIsLight();
            startThemePoller();
        }
        applyTheme(lightTheme);
        applyAccent(accentHex);

        refreshDisplay();
    }

    @Override
    public void stop() {
        saveHistory();
        saveVariables();
        saveSlots();
        saveSettings();
    }

    // ===== Custom title bar (undecorated stage) =====
    private HBox buildTitleBar(Stage stage) {
        HBox bar = new HBox(4);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 4, 2, 12));
        bar.setMinHeight(30);
        bar.setPrefHeight(30);
        bar.setMaxHeight(30);

        Label dot = new Label("●");
        dot.getStyleClass().add("title-dot");
        Label title = new Label("Calculator");
        title.getStyleClass().add("title-label");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button min = new Button("⎯");
        Button max = new Button("▢");
        Button close = new Button("✕");
        for (Button b : new Button[]{min, max, close}) {
            b.getStyleClass().add("title-btn");
            b.setFocusTraversable(false);
        }
        close.getStyleClass().add("title-close");
        min.setTooltip(new Tooltip("Minimizza"));
        max.setTooltip(new Tooltip("Massimizza / Ripristina (doppio click sulla title bar)"));
        close.setTooltip(new Tooltip("Chiudi"));
        min.setOnAction(e -> stage.setIconified(true));
        max.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        close.setOnAction(e -> { saveHistory(); saveVariables(); saveSlots(); Platform.exit(); });

        bar.getChildren().addAll(dot, title, sp, min, max, close);

        final double[] offset = new double[2];
        bar.setOnMousePressed(e -> {
            if (e.getY() < 4) return; // leave top edge for resize
            offset[0] = e.getScreenX() - stage.getX();
            offset[1] = e.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(e -> {
            if (stage.isMaximized()) return;
            if (offset[0] == 0 && offset[1] == 0) return;
            stage.setX(e.getScreenX() - offset[0]);
            stage.setY(e.getScreenY() - offset[1]);
        });
        bar.setOnMouseReleased(e -> { offset[0] = 0; offset[1] = 0; });
        bar.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getY() >= 4) stage.setMaximized(!stage.isMaximized());
        });

        return bar;
    }

    // ===== Resize handlers (on the visible window root) =====
    private void installResizeHandlers(Stage stage, VBox target) {
        final double EDGE = 6;
        final boolean[] resizing = {false};
        final double[] start = new double[6]; // screenX, screenY, stageX, stageY, w, h
        final boolean[] dir = new boolean[4]; // L, T, R, B

        target.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (stage.isMaximized()) { target.getScene().setCursor(Cursor.DEFAULT); return; }
            double x = e.getX(), y = e.getY();
            double w = target.getWidth(), h = target.getHeight();
            boolean L = x < EDGE, R = x > w - EDGE, T = y < EDGE, B = y > h - EDGE;
            Cursor c;
            if      (L && T) c = Cursor.NW_RESIZE;
            else if (R && T) c = Cursor.NE_RESIZE;
            else if (L && B) c = Cursor.SW_RESIZE;
            else if (R && B) c = Cursor.SE_RESIZE;
            else if (L) c = Cursor.W_RESIZE;
            else if (R) c = Cursor.E_RESIZE;
            else if (T) c = Cursor.N_RESIZE;
            else if (B) c = Cursor.S_RESIZE;
            else c = Cursor.DEFAULT;
            target.getScene().setCursor(c);
        });

        target.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (stage.isMaximized()) return;
            double x = e.getX(), y = e.getY();
            double w = target.getWidth(), h = target.getHeight();
            dir[0] = x < EDGE;
            dir[1] = y < EDGE;
            dir[2] = x > w - EDGE;
            dir[3] = y > h - EDGE;
            if (dir[0] || dir[1] || dir[2] || dir[3]) {
                resizing[0] = true;
                start[0] = e.getScreenX();
                start[1] = e.getScreenY();
                start[2] = stage.getX();
                start[3] = stage.getY();
                start[4] = stage.getWidth();
                start[5] = stage.getHeight();
                e.consume();
            }
        });

        target.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!resizing[0]) return;
            double dx = e.getScreenX() - start[0];
            double dy = e.getScreenY() - start[1];
            double minW = stage.getMinWidth();
            double minH = stage.getMinHeight();
            if (dir[2]) stage.setWidth(Math.max(minW, start[4] + dx));
            if (dir[3]) stage.setHeight(Math.max(minH, start[5] + dy));
            if (dir[0]) {
                double newW = Math.max(minW, start[4] - dx);
                stage.setX(start[2] + (start[4] - newW));
                stage.setWidth(newW);
            }
            if (dir[1]) {
                double newH = Math.max(minH, start[5] - dy);
                stage.setY(start[3] + (start[5] - newH));
                stage.setHeight(newH);
            }
            e.consume();
        });

        target.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> resizing[0] = false);
    }

    // ===== UI builders =====
    private VBox buildTopArea() {
        HBox toolbar = new HBox(5);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 2, 6, 2));

        degRadToggle.getStyleClass().add("chip");
        degRadToggle.setTooltip(new Tooltip("Modalità angoli: DEG (gradi) ↔ RAD (radianti)"));
        secondToggle.getStyleClass().add("chip");
        secondToggle.setTooltip(new Tooltip("Funzioni trigonometriche inverse: sin↔asin, cos↔acos, tan↔atan"));
        modeBtn.getStyleClass().addAll("chip", "icon");
        modeBtn.setTooltip(new Tooltip("Apri menu"));
        histBtn.getStyleClass().addAll("chip", "icon");
        saveBtn.getStyleClass().addAll("chip", "icon");
        saveBtn.setTooltip(new Tooltip("Mostra / nascondi slot memoria"));
        pinBtn.getStyleClass().addAll("chip", "icon");
        pinBtn.setTooltip(new Tooltip("Tieni sempre in primo piano (disattiva la trasparenza)"));

        for (var n : new javafx.scene.control.Control[]{degRadToggle, secondToggle, modeBtn, histBtn, saveBtn, pinBtn}) {
            n.setFocusTraversable(false);
        }

        degRadToggle.setOnAction(e -> {
            radians = degRadToggle.isSelected();
            degRadToggle.setText(radians ? "RAD" : "DEG");
            refreshDisplay();
        });
        secondToggle.setOnAction(e -> {
            second = secondToggle.isSelected();
            updateSecondLabels();
            refreshIndicators();
        });
        modeBtn.setOnAction(e -> toggleModePanel());
        histBtn.setOnAction(e -> toggleHistory());
        saveBtn.setOnAction(e -> enterSaveMode());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getChildren().addAll(modeBtn, degRadToggle, secondToggle, saveBtn, spacer, pinBtn);

        display = new VBox(2);
        display.getStyleClass().add("display");
        display.setPadding(new Insets(10, 16, 14, 16));
        display.setMinHeight(100);

        indicatorsLabel.getStyleClass().add("indicators");
        indicatorsLabel.setMaxWidth(Double.MAX_VALUE);
        indicatorsLabel.setAlignment(Pos.CENTER_LEFT);

        exprLabel.getStyleClass().add("expr");
        exprLabel.setMaxWidth(Double.MAX_VALUE);
        exprLabel.setAlignment(Pos.CENTER_RIGHT);

        resultLabel.getStyleClass().add("result");
        resultLabel.setMaxWidth(Double.MAX_VALUE);
        resultLabel.setAlignment(Pos.CENTER_RIGHT);

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        display.getChildren().addAll(indicatorsLabel, grow, exprLabel, resultLabel);
        display.setAlignment(Pos.BOTTOM_RIGHT);

        slotsRow = buildSlotsRow();
        memoryCmds = buildMemoryCmdsRow();

        memOpHint = new Label("");
        memOpHint.getStyleClass().add("mem-op-hint");
        memOpHint.setManaged(false);
        memOpHint.setVisible(false);
        memOpHint.setMaxWidth(Double.MAX_VALUE);
        memOpHint.setAlignment(Pos.CENTER);

        memoryRevealBox = new VBox(4, memOpHint, slotsRow);
        memoryRevealBox.setManaged(false);
        memoryRevealBox.setVisible(false);
        memoryRevealBox.setOpacity(0);

        VBox.setMargin(memoryRevealBox, new Insets(8, 0, 0, 0));
        VBox.setMargin(memoryCmds, new Insets(8, 0, 0, 0));

        calcChrome = new VBox(0, display, memoryRevealBox, memoryCmds);
        return new VBox(0, toolbar, calcChrome);
    }

    private HBox buildMemoryCmdsRow() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER);
        String[] cmds = {"MC", "MR", "M+", "M−"};
        for (String t : cmds) {
            Button b = new Button(t);
            b.getStyleClass().addAll("btn", "mem");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinHeight(38);
            b.setPrefHeight(38);
            b.setFocusTraversable(false);
            HBox.setHgrow(b, Priority.ALWAYS);
            applyTip(b, t);
            b.setOnAction(e -> press(t));
            row.getChildren().add(b);
        }
        return row;
    }

    private HBox buildSlotsRow() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < N_SLOTS; i++) {
            final int idx = i;
            Button b = new Button();
            b.getStyleClass().add("slot-btn");
            b.setFocusTraversable(false);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);

            Label idxLbl = new Label("M" + (i + 1));
            idxLbl.getStyleClass().add("slot-idx");
            Label valLbl = new Label("—");
            valLbl.getStyleClass().add("slot-val");
            VBox content = new VBox(0, idxLbl, valLbl);
            content.setAlignment(Pos.CENTER);
            b.setGraphic(content);

            b.setTooltip(new Tooltip("Click: inserisci/salva  ·  Tasto destro: menu"));
            b.setOnAction(e -> onSlotClick(idx));

            ContextMenu menu = new ContextMenu();
            MenuItem mSave = new MenuItem("Salva risultato qui");
            MenuItem mClear = new MenuItem("Pulisci slot");
            mSave.setOnAction(e -> saveCurrentToSlot(idx));
            mClear.setOnAction(e -> clearSlot(idx));
            menu.getItems().addAll(mSave, mClear);
            b.setContextMenu(menu);

            slotBtns[i] = b;
            slotValLabels[i] = valLbl;
            refreshSlot(i);
            row.getChildren().add(b);
        }
        return row;
    }

    private void enterSaveMode() {
        revealMemoryPanel(!memoryRevealBox.isManaged());
    }

    private void exitSaveMode() {
        revealMemoryPanel(false);
    }

    private void revealMemoryPanel(boolean show) {
        if (show && memoryRevealBox.isManaged()) return;
        if (!show && !memoryRevealBox.isManaged()) {
            if (pendingMemOp != MemMode.NONE) { pendingMemOp = MemMode.NONE; updateMemHint(); }
            return;
        }
        if (!show) {
            pendingMemOp = MemMode.NONE;
            updateMemHint();
        }

        Timeline t = new Timeline();
        double targetH = (pendingMemOp == MemMode.NONE) ? 46 : 70;

        if (show) {
            memoryRevealBox.setVisible(true);
            memoryRevealBox.setManaged(true);
            memoryRevealBox.setOpacity(0);
            memoryRevealBox.setMaxHeight(0);
            memoryRevealBox.setTranslateY(12);
            t.getKeyFrames().add(new KeyFrame(Duration.millis(220),
                new KeyValue(memoryRevealBox.opacityProperty(),    1.0),
                new KeyValue(memoryRevealBox.maxHeightProperty(),  targetH),
                new KeyValue(memoryRevealBox.translateYProperty(), 0.0)
            ));
        } else {
            t.getKeyFrames().add(new KeyFrame(Duration.millis(180),
                new KeyValue(memoryRevealBox.opacityProperty(),    0.0),
                new KeyValue(memoryRevealBox.maxHeightProperty(),  0.0),
                new KeyValue(memoryRevealBox.translateYProperty(), 12.0)
            ));
            t.setOnFinished(e -> {
                memoryRevealBox.setVisible(false);
                memoryRevealBox.setManaged(false);
                memoryRevealBox.setMaxHeight(Double.MAX_VALUE);
            });
        }
        t.play();
    }

    private double currentResultValue() {
        if (expression.length() == 0) return 0;
        if (mode == Mode.PROGRAMMER) {
            return new ProgParser(expression.toString(), base).parse().doubleValue();
        }
        return new Parser(expression.toString(), radians, variables).parse();
    }

    private void onSlotClick(int i) {
        if (pendingMemOp != MemMode.NONE) {
            switch (pendingMemOp) {
                case MC:  clearSlot(i); break;
                case MR:  if (slotSet[i]) insertNumberIntoExpression(slots[i]); break;
                case ADD: addCurrentToSlot(i, +1); break;
                case SUB: addCurrentToSlot(i, -1); break;
                default:  break;
            }
            pendingMemOp = MemMode.NONE;
            updateMemHint();
            revealMemoryPanel(false);
            return;
        }
        if (slotSet[i]) {
            insertNumberIntoExpression(slots[i]);
            if (memoryRevealBox != null && memoryRevealBox.isManaged()) revealMemoryPanel(false);
        } else {
            saveCurrentToSlot(i);
        }
    }

    private void updateMemHint() {
        if (memOpHint == null) return;
        String txt = switch (pendingMemOp) {
            case MC  -> "▼ Clicca uno slot da cancellare";
            case MR  -> "▼ Clicca uno slot per inserirne il valore";
            case ADD -> "▼ Clicca uno slot per sommarci il risultato";
            case SUB -> "▼ Clicca uno slot da cui sottrarre il risultato";
            default  -> "";
        };
        memOpHint.setText(txt);
        boolean show = pendingMemOp != MemMode.NONE;
        memOpHint.setManaged(show);
        memOpHint.setVisible(show);
    }

    private void saveCurrentToSlot(int i) {
        try {
            slots[i] = currentResultValue();
            slotSet[i] = true;
            refreshSlot(i);
            refreshIndicators();
            saveSlots();
        } catch (Exception ignored) {}
    }

    private void clearSlot(int i) {
        slotSet[i] = false;
        slots[i] = 0;
        refreshSlot(i);
        refreshIndicators();
        saveSlots();
    }

    private void refreshSlot(int i) {
        if (slotValLabels[i] == null) return;
        slotValLabels[i].setText(slotSet[i] ? shortFmt(slots[i]) : "—");
        slotBtns[i].pseudoClassStateChanged(SELECTED, slotSet[i]);
    }

    private void insertNumberIntoExpression(double v) {
        if (justEvaluated) {
            expression.setLength(0);
            justEvaluated = false;
        }
        expression.append(Toolkit.fmt(v).replace("−", "-"));
        refreshDisplay();
    }

    private static String shortFmt(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "−∞";
        if (v == 0) return "0";
        double av = Math.abs(v);
        if (av < 1e-3) return String.format(Locale.ROOT, "%.1e", v);
        if (av < 1)    return String.format(Locale.ROOT, "%.3g", v);
        if (av < 1e5) {
            if (v == Math.floor(v)) return Long.toString((long) v);
            return String.format(Locale.ROOT, "%.4g", v);
        }
        if (av < 1e6) return String.format(Locale.ROOT, "%.0fk", v / 1000.0);
        if (av < 1e9) return String.format(Locale.ROOT, "%.1fM", v / 1e6).replace(".0M", "M");
        return String.format(Locale.ROOT, "%.1e", v);
    }

    private void rebuildGrid() {
        grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int c = 0; c < 4; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            grid.getColumnConstraints().add(cc);
        }
        sciButtons.clear();
        progDigitButtons.clear();
        baseButtons.clear();

        String[][] layout = switch (mode) {
            case STANDARD   -> STANDARD_LAYOUT;
            case SCIENTIFIC -> SCIENTIFIC_LAYOUT;
            case PROGRAMMER -> PROGRAMMER_LAYOUT;
            default         -> SCIENTIFIC_LAYOUT;
        };
        for (int r = 0; r < layout.length; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setPercentHeight(100.0 / layout.length);
            grid.getRowConstraints().add(rc);
            for (int c = 0; c < 4; c++) {
                String t = layout[r][c];
                if (t == null || t.isEmpty()) continue;
                grid.add(makeButton(t), c, r);
            }
        }

        if (mode == Mode.SCIENTIFIC) updateSecondLabels();
        else if (mode == Mode.PROGRAMMER) updateBaseUI();

        mainPane.setCenter(grid);
        BorderPane.setMargin(grid, new Insets(8, 0, 0, 0));
    }

    private static final String[][] SCIENTIFIC_LAYOUT = {
        {"sin", "cos", "tan", "π"},
        {"ln",  "log", "√",   "e"},
        {"(",   ")",   "^",   "!"},
        {"AC",  "⌫",   "Ans", "÷"},
        {"7",   "8",   "9",   "×"},
        {"4",   "5",   "6",   "−"},
        {"1",   "2",   "3",   "+"},
        {"±",   "0",   ".",   "="},
    };

    private static final String[][] STANDARD_LAYOUT = {
        {"AC",  "⌫",   "%",   "÷"},
        {"7",   "8",   "9",   "×"},
        {"4",   "5",   "6",   "−"},
        {"1",   "2",   "3",   "+"},
        {"±",   "0",   ".",   "="},
    };

    private static final String[][] PROGRAMMER_LAYOUT = {
        {"DEC", "HEX", "BIN", "OCT"},
        {"AND", "OR",  "XOR", "NOT"},
        {"<<",  ">>",  "(",   ")"},
        {"A",   "B",   "C",   "D"},
        {"E",   "F",   "Ans", "÷"},
        {"7",   "8",   "9",   "×"},
        {"4",   "5",   "6",   "−"},
        {"1",   "2",   "3",   "+"},
        {"AC",  "⌫",   "0",   "="},
    };

    private static final Set<String> SCI_FLIP =
        Set.of("sin", "cos", "tan");

    private static final Map<String, String> BTN_TOOLTIPS = Map.ofEntries(
        Map.entry("sin",  "Seno (DEG/RAD secondo il toggle)"),
        Map.entry("cos",  "Coseno"),
        Map.entry("tan",  "Tangente"),
        Map.entry("asin", "Arcoseno → restituisce l'angolo"),
        Map.entry("acos", "Arcocoseno → restituisce l'angolo"),
        Map.entry("atan", "Arcotangente → restituisce l'angolo"),
        Map.entry("ln",   "Logaritmo naturale (base e)"),
        Map.entry("log",  "Logaritmo decimale (base 10)"),
        Map.entry("√",    "Radice quadrata"),
        Map.entry("!",    "Fattoriale (n!)"),
        Map.entry("π",    "Pi greco ≈ 3.14159265358979"),
        Map.entry("e",    "Costante di Eulero ≈ 2.71828182845905"),
        Map.entry("(",    "Apri parentesi"),
        Map.entry(")",    "Chiudi parentesi"),
        Map.entry("^",    "Elevamento a potenza"),
        Map.entry("MC",   "Cancella memoria — scegli quale slot pulire"),
        Map.entry("MR",   "Inserisci memoria — scegli quale slot inserire"),
        Map.entry("M+",   "Aggiungi a memoria — scegli a quale slot sommare"),
        Map.entry("M−",   "Sottrai da memoria — scegli da quale slot sottrarre"),
        Map.entry("AC",   "Cancella tutta l'espressione (Esc)"),
        Map.entry("⌫",    "Cancella ultimo carattere (Backspace)"),
        Map.entry("Ans",  "Inserisci l'ultimo risultato calcolato"),
        Map.entry("÷",    "Divisione"),
        Map.entry("×",    "Moltiplicazione"),
        Map.entry("−",    "Sottrazione"),
        Map.entry("+",    "Addizione"),
        Map.entry("±",    "Cambia segno dell'ultimo numero"),
        Map.entry("=",    "Calcola il risultato (Enter)"),
        Map.entry("%",    "Percentuale (divide per 100)"),
        Map.entry("DEC",  "Base decimale (10)"),
        Map.entry("HEX",  "Base esadecimale (16) → cifre 0-9, A-F"),
        Map.entry("BIN",  "Base binaria (2) → solo 0 e 1"),
        Map.entry("OCT",  "Base ottale (8)"),
        Map.entry("AND",  "AND bit a bit"),
        Map.entry("OR",   "OR bit a bit"),
        Map.entry("XOR",  "XOR bit a bit (esclusivo)"),
        Map.entry("NOT",  "Negazione bit a bit (complemento)"),
        Map.entry("<<",   "Shift bit a sinistra"),
        Map.entry(">>",   "Shift bit a destra")
    );

    private static void applyTip(Button b, String key) {
        String tip = BTN_TOOLTIPS.get(key);
        if (tip == null) return;
        Tooltip t = new Tooltip(tip);
        t.setShowDelay(Duration.millis(400));
        t.setHideDelay(Duration.millis(200));
        b.setTooltip(t);
    }
    private static final Set<String> PROG_DIGITS =
        Set.of("0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F");
    private static final Set<String> BASE_BTNS =
        Set.of("DEC","HEX","BIN","OCT");
    private static final Set<String> PROG_FNS =
        Set.of("AND","OR","XOR","NOT","<<",">>","(",")");
    private static final Set<String> BASIC_OPS =
        Set.of("+","−","×","÷");

    private Button makeButton(String t) {
        Button b = new Button(t);
        b.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        b.setFocusTraversable(false);
        b.getStyleClass().add("btn");
        styleButton(b, t);
        applyTip(b, t);
        b.setOnAction(e -> press(t));
        if (mode == Mode.SCIENTIFIC && SCI_FLIP.contains(t)) sciButtons.put(t, b);
        if (mode == Mode.PROGRAMMER) {
            if (PROG_DIGITS.contains(t)) progDigitButtons.put(t, b);
            if (BASE_BTNS.contains(t)) baseButtons.put(t, b);
        }
        return b;
    }

    private void styleButton(Button b, String t) {
        if (t.equals("=")) {
            b.getStyleClass().add("eq");
        } else if (BASIC_OPS.contains(t)) {
            b.getStyleClass().add("op");
        } else if (t.equals("AC") || t.equals("⌫")) {
            b.getStyleClass().add("clear");
        } else if (mode == Mode.PROGRAMMER && BASE_BTNS.contains(t)) {
            b.getStyleClass().add("base");
        } else if (mode == Mode.PROGRAMMER && PROG_FNS.contains(t)) {
            b.getStyleClass().add("fn");
        } else if (mode == Mode.PROGRAMMER && Set.of("A","B","C","D","E","F").contains(t)) {
            b.getStyleClass().add("hex");
        } else {
            boolean isDigit = t.length() == 1 && Character.isDigit(t.charAt(0));
            if (!isDigit && !t.equals(".") && !t.equals("±") && !t.equals("Ans")
                    && !t.startsWith("M")) {
                b.getStyleClass().add("fn");
            } else if (t.startsWith("M")) {
                b.getStyleClass().add("mem");
            } else if (t.equals("Ans")) {
                b.getStyleClass().add("ans");
            }
        }
    }

    private void updateSecondLabels() {
        Map<String, String> alt = Map.of(
            "sin", "asin", "cos", "acos", "tan", "atan"
        );
        for (var e : alt.entrySet()) {
            Button b = sciButtons.get(e.getKey());
            if (b != null) {
                String label = second ? e.getValue() : e.getKey();
                b.setText(label);
                applyTip(b, label);
            }
        }
    }

    private void updateBaseUI() {
        Set<String> allowed = switch (base) {
            case 2  -> Set.of("0","1");
            case 8  -> Set.of("0","1","2","3","4","5","6","7");
            case 16 -> Set.of("0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F");
            default -> Set.of("0","1","2","3","4","5","6","7","8","9");
        };
        for (var e : progDigitButtons.entrySet()) {
            e.getValue().setDisable(!allowed.contains(e.getKey()));
        }
        for (var e : baseButtons.entrySet()) {
            e.getValue().pseudoClassStateChanged(SELECTED, baseName(base).equals(e.getKey()));
        }
    }

    private String baseName(int b) {
        return switch (b) {
            case 2 -> "BIN"; case 8 -> "OCT"; case 16 -> "HEX"; default -> "DEC";
        };
    }

    // ===== Input dispatch =====
    private void press(String t) {
        if (justEvaluated) {
            if (isInputStart(t)) expression.setLength(0);
            justEvaluated = false;
        }
        if (mode == Mode.PROGRAMMER) pressProgrammer(t);
        else pressStandard(t);
        refreshDisplay();
    }

    private boolean isInputStart(String t) {
        if (Set.of("AC","=","⌫","M+","M−","MR","MC").contains(t)) return false;
        if (Set.of("+","−","×","÷","^","!","%",")","±",
                   "AND","OR","XOR","NOT","<<",">>",
                   "DEC","HEX","BIN","OCT","2nd").contains(t)) return false;
        return true;
    }

    private void pressStandard(String t) {
        switch (t) {
            case "AC": expression.setLength(0); break;
            case "⌫":  backspace(); break;
            case "=":  evaluate(true); return;
            case "sin": expression.append(second ? "asin(" : "sin("); break;
            case "cos": expression.append(second ? "acos(" : "cos("); break;
            case "tan": expression.append(second ? "atan(" : "tan("); break;
            case "ln":  expression.append("ln("); break;
            case "log": expression.append("log("); break;
            case "√":   expression.append("√("); break;
            case "!":   expression.append("!"); break;
            case "π":   expression.append("π"); break;
            case "e":   expression.append("e"); break;
            case "Ans": expression.append(lastAnswer.replace("−", "-")); break;
            case "±":   toggleSign(); break;
            case "MC":  pendingMemOp = MemMode.MC;  updateMemHint(); revealMemoryPanel(true); break;
            case "MR":  pendingMemOp = MemMode.MR;  updateMemHint(); revealMemoryPanel(true); break;
            case "M+":  pendingMemOp = MemMode.ADD; updateMemHint(); revealMemoryPanel(true); break;
            case "M−":  pendingMemOp = MemMode.SUB; updateMemHint(); revealMemoryPanel(true); break;
            default:    expression.append(t);
        }
    }

    private void pressProgrammer(String t) {
        switch (t) {
            case "AC": expression.setLength(0); break;
            case "⌫":  backspaceProg(); break;
            case "=":  evaluateProgrammer(true); return;
            case "DEC": convertBase(10); break;
            case "HEX": convertBase(16); break;
            case "BIN": convertBase(2);  break;
            case "OCT": convertBase(8);  break;
            case "AND": expression.append(" AND "); break;
            case "OR":  expression.append(" OR ");  break;
            case "XOR": expression.append(" XOR "); break;
            case "NOT": expression.append("NOT ");  break;
            case "<<":  expression.append(" << ");  break;
            case ">>":  expression.append(" >> ");  break;
            case "Ans": expression.append(lastAnswerProg.toString(base).toUpperCase()); break;
            default:    expression.append(t);
        }
    }

    private void backspace() {
        if (expression.length() == 0) return;
        String[] funcs = {"asin(", "acos(", "atan(",
                          "sin(", "cos(", "tan(",
                          "log(", "ln(", "exp(", "10^(", "√("};
        for (String f : funcs) {
            if (endsWith(expression, f)) {
                expression.setLength(expression.length() - f.length());
                return;
            }
        }
        expression.setLength(expression.length() - 1);
    }

    private void backspaceProg() {
        if (expression.length() == 0) return;
        String[] ops = {" AND ", " OR ", " XOR ", "NOT ", " << ", " >> "};
        for (String op : ops) {
            if (endsWith(expression, op)) {
                expression.setLength(expression.length() - op.length());
                return;
            }
        }
        expression.setLength(expression.length() - 1);
    }

    private boolean endsWith(StringBuilder sb, String s) {
        int n = s.length();
        if (sb.length() < n) return false;
        for (int i = 0; i < n; i++) {
            if (sb.charAt(sb.length() - n + i) != s.charAt(i)) return false;
        }
        return true;
    }

    private void toggleSign() {
        if (expression.length() == 0) { expression.append("−"); return; }
        int i = expression.length();
        while (i > 0) {
            char c = expression.charAt(i - 1);
            if (Character.isDigit(c) || c == '.') i--;
            else break;
        }
        if (i == expression.length()) expression.append("−");
        else if (i > 0 && (expression.charAt(i - 1) == '−' || expression.charAt(i - 1) == '-'))
            expression.deleteCharAt(i - 1);
        else expression.insert(i, "−");
    }

    private void addCurrentToSlot(int i, int sign) {
        try {
            double v = currentResultValue();
            double existing = slotSet[i] ? slots[i] : 0;
            slots[i] = existing + sign * v;
            slotSet[i] = true;
            refreshSlot(i);
            refreshIndicators();
            saveSlots();
        } catch (Exception ignored) {}
    }

    private void convertBase(int newBase) {
        try {
            BigInteger v = new ProgParser(expression.toString(), base).parse();
            base = newBase;
            expression.setLength(0);
            expression.append(v.toString(newBase).toUpperCase());
        } catch (Exception ignored) {
            base = newBase;
            expression.setLength(0);
        }
        updateBaseUI();
    }

    // ===== Evaluation =====
    private void evaluate(boolean commit) {
        if (expression.length() == 0) {
            resultLabel.setText("0");
            exprLabel.setText("");
            return;
        }
        try {
            double v = new Parser(expression.toString(), radians, variables).parse();
            String s = formatNumber(v);
            resultLabel.setText(s);
            if (commit) {
                String exprStr = expression.toString();
                exprLabel.setText(exprStr + " =");
                addHistory(exprStr, s);
                lastAnswer = s;
                expression.setLength(0);
                expression.append(s.replace("−", "-"));
                justEvaluated = true;
            } else {
                exprLabel.setText(expression.toString());
            }
        } catch (Exception ex) {
            if (commit) resultLabel.setText("Error");
            exprLabel.setText(expression.toString());
        }
    }

    private void evaluateProgrammer(boolean commit) {
        if (expression.length() == 0) {
            resultLabel.setText("0");
            exprLabel.setText("");
            return;
        }
        try {
            BigInteger v = new ProgParser(expression.toString(), base).parse();
            String s = v.toString(base).toUpperCase();
            resultLabel.setText(s);
            if (commit) {
                String exprStr = expression.toString();
                exprLabel.setText(exprStr + " =");
                addHistory("[" + baseName(base) + "] " + exprStr, s);
                lastAnswer = s;
                lastAnswerProg = v;
                expression.setLength(0);
                expression.append(s);
                justEvaluated = true;
            } else {
                exprLabel.setText(expression.toString());
            }
        } catch (Exception ex) {
            if (commit) resultLabel.setText("Error");
            exprLabel.setText(expression.toString());
        }
    }

    private void addHistory(String expr, String result) {
        history.add(0, new String[]{expr, result});
        while (history.size() > HISTORY_MAX) history.remove(history.size() - 1);
        refreshHistoryList();
    }

    private String formatNumber(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "−∞";
        if (v == 0) return "0";
        return switch (notation) {
            case SCIENTIFIC  -> sciFmt(v).replace("-", "−");
            case ENGINEERING -> engFmt(v).replace("-", "−");
            default          -> autoFmt(v).replace("-", "−");
        };
    }

    private String autoFmt(double v) {
        if (v == Math.floor(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%." + decimalPlaces + "g", v).trim();
        if (s.contains(".") && !s.toLowerCase().contains("e"))
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private String sciFmt(double v) {
        return String.format(Locale.ROOT, "%." + decimalPlaces + "e", v);
    }

    private String engFmt(double v) {
        if (v == 0) return "0";
        int exp = (int) Math.floor(Math.log10(Math.abs(v)));
        exp = (int) (3.0 * Math.floor(exp / 3.0));
        double mant = v / Math.pow(10, exp);
        String m = String.format(Locale.ROOT, "%." + Math.max(0, decimalPlaces - 1) + "g", mant);
        if (m.contains(".")) m = m.replaceAll("0+$", "").replaceAll("\\.$", "");
        return exp == 0 ? m : m + " × 10" + supExp(exp);
    }

    private String supExp(int n) {
        String s = Integer.toString(n);
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '-': out.append('⁻'); break;
                case '0': out.append('⁰'); break;
                case '1': out.append('¹'); break;
                case '2': out.append('²'); break;
                case '3': out.append('³'); break;
                case '4': out.append('⁴'); break;
                case '5': out.append('⁵'); break;
                case '6': out.append('⁶'); break;
                case '7': out.append('⁷'); break;
                case '8': out.append('⁸'); break;
                case '9': out.append('⁹'); break;
            }
        }
        return out.toString();
    }

    private void refreshDisplay() {
        refreshIndicators();
        if (justEvaluated) return;
        if (mode == Mode.PROGRAMMER) evaluateProgrammer(false);
        else evaluate(false);
    }

    private void refreshIndicators() {
        StringBuilder s = new StringBuilder();
        boolean anyMem = false;
        for (boolean st : slotSet) if (st) { anyMem = true; break; }
        if (anyMem) s.append("M  ");
        s.append(mode.label.toUpperCase()).append("  ");
        if (mode == Mode.PROGRAMMER) {
            s.append(baseName(base));
        } else if (mode == Mode.SCIENTIFIC || mode == Mode.STANDARD) {
            s.append(radians ? "RAD" : "DEG");
            if (second && mode == Mode.SCIENTIFIC) s.append("  2nd");
        }
        if (!variables.isEmpty()) s.append("  ·  ").append(variables.size()).append(" var");
        indicatorsLabel.setText(s.toString());
    }

    // ===== Keyboard =====
    private void handleKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            if (anyOverlayVisible()) { hideAllOverlays(); e.consume(); return; }
        }
        if (anyOverlayVisible()) return;
        if (mode == Mode.DATE || mode == Mode.GRAPHING) return; // mode-specific UI owns the keyboard
        if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.C) {
            copyExpressionAndResult(); e.consume(); return;
        }
        if (e.isControlDown() && e.getCode() == KeyCode.C) { copyResult(); e.consume(); return; }
        if (e.isControlDown() && e.getCode() == KeyCode.V) { pasteFromClipboard(); e.consume(); return; }
        switch (e.getCode()) {
            case ENTER, EQUALS -> { press("="); e.consume(); }
            case BACK_SPACE    -> { press("⌫"); e.consume(); }
            case ESCAPE, DELETE -> { press("AC"); e.consume(); }
            default -> {}
        }
    }

    private void handleKeyTyped(KeyEvent e) {
        if (e.isControlDown()) return;
        if (anyOverlayVisible()) return;
        if (mode == Mode.DATE || mode == Mode.GRAPHING) return;
        String t = e.getCharacter();
        if (t == null || t.isEmpty()) return;
        char c = t.charAt(0);
        if (c < 32) return;
        if (Character.isDigit(c)) press(String.valueOf(c));
        else if (c == '+') press("+");
        else if (c == '-') press("−");
        else if (c == '*') press("×");
        else if (c == '/') press("÷");
        else if (c == '(') press("(");
        else if (c == ')') press(")");
        else if (mode != Mode.PROGRAMMER) {
            if (c == '^') press("^");
            else if (c == '!') press("!");
            else if (c == '.' || c == ',') press(".");
        } else if (base == 16) {
            char up = Character.toUpperCase(c);
            if (up >= 'A' && up <= 'F') press(String.valueOf(up));
        }
    }

    private void copyResult() {
        Clipboard cb = Clipboard.getSystemClipboard();
        ClipboardContent cc = new ClipboardContent();
        cc.putString(resultLabel.getText());
        cb.setContent(cc);
    }

    private void copyExpressionAndResult() {
        Clipboard cb = Clipboard.getSystemClipboard();
        ClipboardContent cc = new ClipboardContent();
        String e = exprLabel.getText();
        String r = resultLabel.getText();
        String full = (e == null || e.isBlank())
            ? r
            : (e.endsWith("=") ? e + " " + r : e + " = " + r);
        cc.putString(full);
        cb.setContent(cc);
    }

    private void pasteFromClipboard() {
        Clipboard cb = Clipboard.getSystemClipboard();
        String s = cb.getString();
        if (s == null) return;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) press(String.valueOf(c));
            else if (c == '+') press("+");
            else if (c == '-' || c == '−') press("−");
            else if (c == '*' || c == '×') press("×");
            else if (c == '/' || c == '÷') press("÷");
            else if (c == '.') { if (mode == Mode.STANDARD) press("."); }
            else if (c == '(') press("(");
            else if (c == ')') press(")");
        }
    }

    // ===== Overlays =====
    private VBox buildHistoryPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("history-panel");
        panel.setPadding(new Insets(16));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Storico");
        title.getStyleClass().add("history-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button clearAll = new Button("Pulisci");
        clearAll.getStyleClass().add("chip");
        clearAll.setFocusTraversable(false);
        clearAll.setOnAction(e -> { history.clear(); refreshHistoryList(); });
        Button close = new Button("✕");
        close.getStyleClass().addAll("chip", "icon");
        close.setFocusTraversable(false);
        close.setOnAction(e -> toggleHistory());
        header.getChildren().addAll(title, sp, clearAll, close);

        historyList = new ListView<>();
        historyList.getStyleClass().add("history-list");
        historyList.setPlaceholder(new Label("— nessun calcolo ancora —"));
        VBox.setVgrow(historyList, Priority.ALWAYS);
        historyList.setOnMouseClicked(e -> {
            String sel = historyList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            int idx = sel.lastIndexOf(" = ");
            if (idx < 0) return;
            String exprPart = sel.substring(0, idx);
            int prefixEnd = exprPart.indexOf("] ");
            if (prefixEnd > 0 && exprPart.startsWith("[")) exprPart = exprPart.substring(prefixEnd + 2);
            expression.setLength(0);
            expression.append(exprPart);
            justEvaluated = false;
            refreshDisplay();
            toggleHistory();
        });
        refreshHistoryList();

        panel.getChildren().addAll(header, historyList);
        return panel;
    }

    private void refreshHistoryList() {
        if (historyList == null) return;
        List<String> items = new ArrayList<>();
        for (String[] e : history) items.add(e[0] + " = " + e[1]);
        historyList.getItems().setAll(items);
    }

    private void hideAllOverlays() {
        historyPanel.setVisible(false);
        toolkitPanel.setVisible(false);
        modePanel.setVisible(false);
        if (settingsPanel != null) settingsPanel.setVisible(false);
        if (aboutPanel != null)    aboutPanel.setVisible(false);
    }

    private boolean anyOverlayVisible() {
        return historyPanel.isVisible()
            || toolkitPanel.isVisible()
            || modePanel.isVisible()
            || (settingsPanel != null && settingsPanel.isVisible())
            || (aboutPanel != null && aboutPanel.isVisible());
    }

    private void toggleHistory() {
        boolean wasVisible = historyPanel.isVisible();
        hideAllOverlays();
        if (!wasVisible) historyPanel.setVisible(true);
    }

    private void toggleToolkit() {
        boolean wasVisible = toolkitPanel.isVisible();
        hideAllOverlays();
        if (!wasVisible) toolkitPanel.setVisible(true);
    }

    private void toggleModePanel() {
        boolean wasVisible = modePanel.isVisible();
        hideAllOverlays();
        refreshModeRows();
        if (!wasVisible) modePanel.setVisible(true);
    }

    private void backFromToolkitToMenu() {
        hideAllOverlays();
        refreshModeRows();
        modePanel.setVisible(true);
    }

    private void refreshModeRows() {
        for (var e : modeRows.entrySet()) {
            e.getValue().pseudoClassStateChanged(SELECTED, e.getKey() == mode);
        }
    }

    private Node buildModePanel() {
        VBox content = new VBox(6);
        content.setPadding(new Insets(16));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Menu");
        title.getStyleClass().add("history-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().addAll("chip", "icon");
        close.setFocusTraversable(false);
        close.setOnAction(e -> toggleModePanel());
        header.getChildren().addAll(title, sp, close);
        content.getChildren().add(header);

        content.getChildren().add(menuSection("MODALITÀ"));
        for (Mode m : Mode.values()) {
            Button row = menuRowButton(m.label);
            row.setOnAction(e -> { toggleModePanel(); switchToMode(m); });
            modeRows.put(m, row);
            content.getChildren().add(row);
        }

        content.getChildren().add(menuSection("TOOLKIT"));
        content.getChildren().add(menuRow("Conversioni unità",     () -> openToolkitAt("Unità")));
        content.getChildren().add(menuRow("Materiali",              () -> openToolkitAt("Materiali")));
        content.getChildren().add(menuRow("Sezioni & Profili",      () -> openToolkitAt("Sezioni")));
        content.getChildren().add(menuRow("Formulario",             () -> openToolkitAt("Formule")));
        content.getChildren().add(menuRow("Procedure guidate (Bulloni, Saldature, Statistica, Matrici, Tempo)", () -> openToolkitAt("Procedure")));
        content.getChildren().add(menuRow("Strumenti (Triangoli, Vettori, Solver, Tubi, Variabili)", () -> openToolkitAt("Strumenti")));

        content.getChildren().add(menuSection("DATI"));
        content.getChildren().add(menuRow("Storico calcoli",        () -> { toggleModePanel(); toggleHistory(); }));

        content.getChildren().add(menuSection("SISTEMA"));
        content.getChildren().add(menuRow("Impostazioni",           () -> { toggleModePanel(); openSettings(); }));
        content.getChildren().add(menuRow("About / Scorciatoie",    () -> { toggleModePanel(); openAbout(); }));

        ScrollPane sp2 = new ScrollPane(content);
        sp2.setFitToWidth(true);
        sp2.getStyleClass().add("toolkit-scroll");

        VBox panel = new VBox(sp2);
        panel.getStyleClass().add("history-panel");
        VBox.setVgrow(sp2, Priority.ALWAYS);
        return panel;
    }

    private Label menuSection(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("menu-section");
        return l;
    }

    private Button menuRow(String text, Runnable action) {
        Button b = menuRowButton(text);
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button menuRowButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("mode-row");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setFocusTraversable(false);
        return b;
    }

    private void openToolkitAt(String tabName) {
        toggleModePanel();
        if (!toolkitPanel.isVisible()) {
            if (historyPanel.isVisible()) historyPanel.setVisible(false);
            if (settingsPanel.isVisible()) settingsPanel.setVisible(false);
            if (aboutPanel.isVisible())    aboutPanel.setVisible(false);
            toolkitPanel.setVisible(true);
        }
        toolkit.selectTab(tabName);
    }

    private void openSettings() {
        if (toolkitPanel.isVisible()) toolkitPanel.setVisible(false);
        if (historyPanel.isVisible()) historyPanel.setVisible(false);
        if (aboutPanel.isVisible())   aboutPanel.setVisible(false);
        settingsPanel.setVisible(true);
    }

    private void closeSettings() { settingsPanel.setVisible(false); }

    private void openAbout() {
        if (toolkitPanel.isVisible()) toolkitPanel.setVisible(false);
        if (historyPanel.isVisible()) historyPanel.setVisible(false);
        if (settingsPanel.isVisible()) settingsPanel.setVisible(false);
        aboutPanel.setVisible(true);
    }

    private void closeAbout() { aboutPanel.setVisible(false); }

    private Node buildSettingsPanel() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(16));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Impostazioni");
        title.getStyleClass().add("history-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().addAll("chip", "icon");
        close.setFocusTraversable(false);
        close.setOnAction(e -> closeSettings());
        header.getChildren().addAll(title, sp, close);
        content.getChildren().add(header);

        // Accent color
        content.getChildren().add(menuSection("COLORE PRINCIPALE"));
        ColorPicker cp = new ColorPicker(javafx.scene.paint.Color.web(accentHex));
        cp.setMaxWidth(Double.MAX_VALUE);
        cp.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null) applyAccent(colorToHex(nv));
        });
        Button resetAccent = new Button("Ripristina corallo");
        resetAccent.getStyleClass().add("chip");
        resetAccent.setMaxWidth(Double.MAX_VALUE);
        resetAccent.setOnAction(e -> {
            cp.setValue(javafx.scene.paint.Color.web(DEFAULT_ACCENT));
            applyAccent(DEFAULT_ACCENT);
        });
        content.getChildren().addAll(cp, resetAccent);

        // Notation
        content.getChildren().add(menuSection("NOTAZIONE NUMERICA"));
        ToggleGroup notGroup = new ToggleGroup();
        RadioButton rAuto = new RadioButton("Automatica (intera o decimale)");
        RadioButton rSci  = new RadioButton("Scientifica (1.5e+06)");
        RadioButton rEng  = new RadioButton("Ingegneristica (1.5 × 10⁶)");
        rAuto.setToggleGroup(notGroup);
        rSci .setToggleGroup(notGroup);
        rEng .setToggleGroup(notGroup);
        rAuto.getStyleClass().add("settings-radio");
        rSci .getStyleClass().add("settings-radio");
        rEng .getStyleClass().add("settings-radio");
        rAuto.setSelected(notation == Notation.AUTO);
        rSci .setSelected(notation == Notation.SCIENTIFIC);
        rEng .setSelected(notation == Notation.ENGINEERING);
        rAuto.setOnAction(e -> { notation = Notation.AUTO;        refreshDisplay(); saveSettings(); });
        rSci .setOnAction(e -> { notation = Notation.SCIENTIFIC;  refreshDisplay(); saveSettings(); });
        rEng .setOnAction(e -> { notation = Notation.ENGINEERING; refreshDisplay(); saveSettings(); });
        content.getChildren().addAll(rAuto, rSci, rEng);

        // Decimals
        content.getChildren().add(menuSection("CIFRE SIGNIFICATIVE"));
        Slider sl = new Slider(2, 12, decimalPlaces);
        sl.setShowTickLabels(true);
        sl.setShowTickMarks(true);
        sl.setMajorTickUnit(2);
        sl.setMinorTickCount(1);
        sl.setSnapToTicks(true);
        sl.setBlockIncrement(1);
        Label slLbl = new Label("Cifre: " + decimalPlaces);
        slLbl.getStyleClass().add("toolkit-key");
        sl.valueProperty().addListener((o, ov, nv) -> {
            decimalPlaces = (int) Math.round(nv.doubleValue());
            slLbl.setText("Cifre: " + decimalPlaces);
            refreshDisplay();
            saveSettings();
        });
        content.getChildren().addAll(slLbl, sl);

        // Theme
        content.getChildren().add(menuSection("TEMA"));
        CheckBox autoCb = new CheckBox("Segui automaticamente il tema di sistema");
        autoCb.getStyleClass().add("settings-radio");
        autoCb.setSelected(autoTheme);

        ToggleGroup themeGroup = new ToggleGroup();
        RadioButton tDark  = new RadioButton("Scuro");
        RadioButton tLight = new RadioButton("Chiaro");
        tDark.setToggleGroup(themeGroup);
        tLight.setToggleGroup(themeGroup);
        tDark.getStyleClass().add("settings-radio");
        tLight.getStyleClass().add("settings-radio");
        tDark.setSelected(!lightTheme);
        tLight.setSelected(lightTheme);
        tDark.setDisable(autoTheme);
        tLight.setDisable(autoTheme);
        tDark.setOnAction(e -> applyTheme(false));
        tLight.setOnAction(e -> applyTheme(true));

        autoCb.setOnAction(e -> {
            autoTheme = autoCb.isSelected();
            tDark.setDisable(autoTheme);
            tLight.setDisable(autoTheme);
            if (autoTheme) {
                startThemePoller();
                boolean systemLight = detectSystemThemeIsLight();
                applyTheme(systemLight);
                if (systemLight) tLight.setSelected(true); else tDark.setSelected(true);
            } else {
                stopThemePoller();
            }
            saveSettings();
        });
        content.getChildren().addAll(autoCb, tDark, tLight);

        ScrollPane sp2 = new ScrollPane(content);
        sp2.setFitToWidth(true);
        sp2.getStyleClass().add("toolkit-scroll");
        VBox panel = new VBox(sp2);
        panel.getStyleClass().add("history-panel");
        VBox.setVgrow(sp2, Priority.ALWAYS);
        return panel;
    }

    private void applyTheme(boolean light) {
        lightTheme = light;
        Scene scene = modeBtn.getScene();
        if (scene == null) return;
        Node root = scene.getRoot();
        if (light) {
            if (!root.getStyleClass().contains("light")) root.getStyleClass().add("light");
        } else {
            root.getStyleClass().remove("light");
        }
        saveSettings();
    }

    private Node buildAboutPanel() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("About / Scorciatoie");
        title.getStyleClass().add("history-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().addAll("chip", "icon");
        close.setFocusTraversable(false);
        close.setOnAction(e -> closeAbout());
        header.getChildren().addAll(title, sp, close);
        content.getChildren().add(header);

        Label intro = new Label("Calculator meccanica — strumento orientato a progettazione supporti.");
        intro.getStyleClass().add("toolkit-key");
        intro.setWrapText(true);
        content.getChildren().add(intro);

        content.getChildren().add(menuSection("TASTIERA"));
        String[][] shortcuts = {
            {"Enter / =",          "Calcola"},
            {"Backspace",          "Cancella ultimo carattere"},
            {"Esc",                "AC (o chiude overlay)"},
            {"Ctrl+C",             "Copia risultato"},
            {"Ctrl+Shift+C",       "Copia espressione + risultato"},
            {"Ctrl+V",             "Incolla nell'espressione"},
            {"+ − × ÷ ^ ! % . ( )","Operatori"},
        };
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(4);
        for (int i = 0; i < shortcuts.length; i++) {
            Label k = new Label(shortcuts[i][0]); k.getStyleClass().add("toolkit-key");
            Label v = new Label(shortcuts[i][1]); v.getStyleClass().add("toolkit-val");
            g.add(k, 0, i); g.add(v, 1, i);
        }
        content.getChildren().add(g);

        content.getChildren().add(menuSection("DATI PERSISTITI"));
        Label persist = new Label("%APPDATA%\\scientific-calculator\\\n  · history.txt   (50 ultime espressioni)\n  · variables.txt (variabili nominate)\n  · slots.txt     (M1-M5)");
        persist.getStyleClass().add("toolkit-val");
        content.getChildren().add(persist);

        content.getChildren().add(menuSection("VERSIONE"));
        Label ver = new Label("v0.5 — sviluppato per workflow supporti meccanici");
        ver.getStyleClass().add("toolkit-val");
        content.getChildren().add(ver);

        ScrollPane sp2 = new ScrollPane(content);
        sp2.setFitToWidth(true);
        sp2.getStyleClass().add("toolkit-scroll");
        VBox panel = new VBox(sp2);
        panel.getStyleClass().add("history-panel");
        VBox.setVgrow(sp2, Priority.ALWAYS);
        return panel;
    }

    private void switchToMode(Mode m) {
        if (mode == m) return;
        mode = m;
        expression.setLength(0);
        second = false;
        secondToggle.setSelected(false);
        base = 10;
        justEvaluated = false;

        if (memoryRevealBox != null && memoryRevealBox.isManaged()) {
            revealMemoryPanel(false);
        }

        boolean isCalc = m == Mode.STANDARD || m == Mode.SCIENTIFIC || m == Mode.PROGRAMMER;
        calcChrome.setVisible(isCalc);
        calcChrome.setManaged(isCalc);

        if (memoryCmds != null) {
            boolean showMem = m == Mode.STANDARD || m == Mode.SCIENTIFIC;
            memoryCmds.setVisible(showMem);
            memoryCmds.setManaged(showMem);
        }

        if (isCalc) {
            rebuildGrid();
        } else if (m == Mode.DATE) {
            if (dateView == null) dateView = buildDateView();
            mainPane.setCenter(dateView);
            BorderPane.setMargin((Node) dateView, new Insets(8, 0, 0, 0));
        } else if (m == Mode.GRAPHING) {
            if (graphView == null) graphView = buildGraphView();
            mainPane.setCenter(graphView);
            BorderPane.setMargin((Node) graphView, new Insets(8, 0, 0, 0));
        }
        refreshDisplay();
    }

    // ===== Bridge for Toolkit =====
    private Toolkit.Bridge buildBridge() {
        return new Toolkit.Bridge() {
            @Override public void insert(String s) {
                if (justEvaluated) {
                    expression.setLength(0);
                    justEvaluated = false;
                }
                expression.append(s);
                refreshDisplay();
            }
            @Override public void setVariable(String name, double value) {
                variables.put(name, value);
                refreshIndicators();
                fireVarChanged();
                saveVariables();
            }
            @Override public Map<String, Double> variables() { return variables; }
            @Override public void removeVariable(String name) {
                variables.remove(name);
                refreshIndicators();
                fireVarChanged();
                saveVariables();
            }
            @Override public boolean radians() { return radians; }
            @Override public void addVariableChangeListener(Runnable r) { varListeners.add(r); }
        };
    }

    private void fireVarChanged() {
        for (Runnable r : varListeners) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    // ===== Persistence =====
    private static Path resolveAppDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null) appdata = System.getProperty("user.home");
        return Paths.get(appdata, "scientific-calculator");
    }

    private void loadHistory() {
        try {
            if (!Files.exists(HISTORY_PATH)) return;
            for (String line : Files.readAllLines(HISTORY_PATH, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                history.add(new String[]{line.substring(0, tab), line.substring(tab + 1)});
            }
        } catch (IOException ignored) {}
    }

    private void saveHistory() {
        try {
            Files.createDirectories(HISTORY_PATH.getParent());
            List<String> lines = new ArrayList<>();
            for (String[] e : history) lines.add(e[0] + "\t" + e[1]);
            Files.write(HISTORY_PATH, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private void loadVariables() {
        try {
            if (!Files.exists(VARS_PATH)) return;
            for (String line : Files.readAllLines(VARS_PATH, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                try {
                    variables.put(line.substring(0, eq).trim(),
                                  Double.parseDouble(line.substring(eq + 1).trim()));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private void saveVariables() {
        try {
            Files.createDirectories(VARS_PATH.getParent());
            List<String> lines = new ArrayList<>();
            for (var e : variables.entrySet()) lines.add(e.getKey() + "=" + e.getValue());
            Files.write(VARS_PATH, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private void loadSlots() {
        try {
            if (!Files.exists(SLOTS_PATH)) return;
            List<String> lines = Files.readAllLines(SLOTS_PATH, StandardCharsets.UTF_8);
            for (int i = 0; i < Math.min(lines.size(), N_SLOTS); i++) {
                String t = lines.get(i).trim();
                if (t.isEmpty() || t.equals("—")) { slotSet[i] = false; continue; }
                try { slots[i] = Double.parseDouble(t); slotSet[i] = true; }
                catch (NumberFormatException ignored) { slotSet[i] = false; }
            }
        } catch (IOException ignored) {}
    }

    private void saveSlots() {
        try {
            Files.createDirectories(SLOTS_PATH.getParent());
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < N_SLOTS; i++) lines.add(slotSet[i] ? Double.toString(slots[i]) : "—");
            Files.write(SLOTS_PATH, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private void loadSettings() {
        try {
            if (!Files.exists(SETTINGS_PATH)) return;
            for (String line : Files.readAllLines(SETTINGS_PATH, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                switch (k) {
                    case "accent":    if (v.matches("#[0-9A-Fa-f]{6}")) accentHex = v; break;
                    case "theme":     lightTheme = "light".equalsIgnoreCase(v); break;
                    case "autoTheme": autoTheme = Boolean.parseBoolean(v); break;
                    case "notation":  try { notation = Notation.valueOf(v); } catch (Exception ignored) {} break;
                    case "decimals":  try { decimalPlaces = Integer.parseInt(v); } catch (Exception ignored) {} break;
                }
            }
        } catch (IOException ignored) {}
    }

    private void saveSettings() {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("accent="    + accentHex);
            lines.add("theme="     + (lightTheme ? "light" : "dark"));
            lines.add("autoTheme=" + autoTheme);
            lines.add("notation="  + notation.name());
            lines.add("decimals="  + decimalPlaces);
            Files.write(SETTINGS_PATH, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private void applyAccent(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) return;
        accentHex = hex;
        Scene scene = modeBtn.getScene();
        if (scene == null) return;
        Node wr = scene.lookup(".window-root");
        if (wr != null) wr.setStyle("-accent: " + hex + ";");
        saveSettings();
    }

    private static String colorToHex(javafx.scene.paint.Color c) {
        return String.format("#%02X%02X%02X",
            (int) Math.round(c.getRed()   * 255),
            (int) Math.round(c.getGreen() * 255),
            (int) Math.round(c.getBlue()  * 255));
    }

    private boolean detectSystemThemeIsLight() {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        return line.trim().endsWith("0x1");
                    }
                }
            }
            p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return false;
    }

    private void startThemePoller() {
        if (themePoller != null) return;
        themePoller = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (!autoTheme) return;
            boolean systemLight = detectSystemThemeIsLight();
            if (systemLight != lightTheme) applyTheme(systemLight);
        }));
        themePoller.setCycleCount(Timeline.INDEFINITE);
        themePoller.play();
    }

    private void stopThemePoller() {
        if (themePoller != null) {
            themePoller.stop();
            themePoller = null;
        }
    }

    // ===== Date Calculation view =====
    private Node buildDateView() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(8, 4, 8, 4));

        // Card 1: Differenza date
        VBox card1 = new VBox(8);
        card1.getStyleClass().add("date-card");
        card1.setPadding(new Insets(12));
        Label t1 = new Label("Differenza tra date");
        t1.getStyleClass().add("date-card-title");

        DatePicker from = new DatePicker(LocalDate.now());
        DatePicker to = new DatePicker(LocalDate.now().plusDays(30));
        from.setMaxWidth(Double.MAX_VALUE);
        to.setMaxWidth(Double.MAX_VALUE);

        Label diffOut = new Label("—");
        diffOut.getStyleClass().add("toolkit-result");
        diffOut.setMaxWidth(Double.MAX_VALUE);

        Runnable updateDiff = () -> {
            if (from.getValue() == null || to.getValue() == null) { diffOut.setText("—"); return; }
            long days = ChronoUnit.DAYS.between(from.getValue(), to.getValue());
            long weeks = days / 7;
            long absDays = Math.abs(days);
            long years = ChronoUnit.YEARS.between(
                days >= 0 ? from.getValue() : to.getValue(),
                days >= 0 ? to.getValue()   : from.getValue());
            long months = ChronoUnit.MONTHS.between(
                days >= 0 ? from.getValue() : to.getValue(),
                days >= 0 ? to.getValue()   : from.getValue());
            String sign = days < 0 ? "−" : "";
            diffOut.setText(sign + absDays + " giorni · " + (days/7) + " sett. · ≈ " + months + " mesi · ≈ " + years + " anni");
        };
        from.valueProperty().addListener((o, ov, nv) -> updateDiff.run());
        to.valueProperty().addListener((o, ov, nv) -> updateDiff.run());
        updateDiff.run();

        Label fromLbl = new Label("Dal:"); fromLbl.getStyleClass().add("toolkit-key");
        Label toLbl   = new Label("Al:");  toLbl.getStyleClass().add("toolkit-key");
        card1.getChildren().addAll(t1, fromLbl, from, toLbl, to, diffOut);

        // Card 2: Aggiungi/sottrai giorni
        VBox card2 = new VBox(8);
        card2.getStyleClass().add("date-card");
        card2.setPadding(new Insets(12));
        Label t2 = new Label("Aggiungi / sottrai giorni");
        t2.getStyleClass().add("date-card-title");

        DatePicker baseDate = new DatePicker(LocalDate.now());
        baseDate.setMaxWidth(Double.MAX_VALUE);
        TextField daysField = new TextField("30");
        daysField.getStyleClass().add("toolkit-input");
        Label resultOut = new Label("—");
        resultOut.getStyleClass().add("toolkit-result");
        resultOut.setMaxWidth(Double.MAX_VALUE);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy (EEEE)", Locale.ITALIAN);
        Runnable updateAdd = () -> {
            try {
                if (baseDate.getValue() == null) { resultOut.setText("—"); return; }
                long n = Long.parseLong(daysField.getText().trim());
                LocalDate r = baseDate.getValue().plusDays(n);
                resultOut.setText(r.format(fmt));
            } catch (Exception ex) {
                resultOut.setText("—");
            }
        };
        baseDate.valueProperty().addListener((o, ov, nv) -> updateAdd.run());
        daysField.textProperty().addListener((o, ov, nv) -> updateAdd.run());
        updateAdd.run();

        Label baseLbl = new Label("Data:");          baseLbl.getStyleClass().add("toolkit-key");
        Label dLbl    = new Label("Giorni (+/−):");  dLbl.getStyleClass().add("toolkit-key");
        card2.getChildren().addAll(t2, baseLbl, baseDate, dLbl, daysField, resultOut);

        content.getChildren().addAll(card1, card2);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("toolkit-scroll");
        return sp;
    }

    // ===== Graphing view =====
    private Node buildGraphView() {
        VBox v = new VBox(6);
        v.setPadding(new Insets(8, 4, 8, 4));

        HBox row1 = new HBox(6);
        row1.setAlignment(Pos.CENTER_LEFT);
        Label yLbl = new Label("y =");
        yLbl.getStyleClass().add("toolkit-key");
        TextField fnField = new TextField("sin(x)");
        fnField.getStyleClass().add("toolkit-input");
        fnField.setPromptText("es. sin(x), x^2-4, exp(-x/5)*cos(x)");
        HBox.setHgrow(fnField, Priority.ALWAYS);
        row1.getChildren().addAll(yLbl, fnField);

        HBox row2 = new HBox(6);
        row2.setAlignment(Pos.CENTER_LEFT);
        TextField xMinF = new TextField("-10");
        TextField xMaxF = new TextField("10");
        xMinF.getStyleClass().add("toolkit-input");
        xMaxF.getStyleClass().add("toolkit-input");
        xMinF.setPrefWidth(70);
        xMaxF.setPrefWidth(70);
        Label l1 = new Label("x:"); l1.getStyleClass().add("toolkit-key");
        Label l2 = new Label("→"); l2.getStyleClass().add("toolkit-key");
        Button plotBtn = new Button("Plotta");
        plotBtn.getStyleClass().add("eq-chip");
        plotBtn.setFocusTraversable(false);
        row2.getChildren().addAll(l1, xMinF, l2, xMaxF, plotBtn);

        final Canvas canvas = new Canvas(340, 380);
        Pane wrap = new Pane(canvas) {
            @Override protected void layoutChildren() {
                double w = getWidth();
                double h = getHeight();
                if (Math.abs(canvas.getWidth() - w) > 0.5 || Math.abs(canvas.getHeight() - h) > 0.5) {
                    canvas.setWidth(w);
                    canvas.setHeight(h);
                }
            }
        };
        wrap.getStyleClass().add("graph-canvas");
        wrap.setMinSize(0, 0);
        wrap.setPrefSize(340, 380);
        wrap.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(wrap, Priority.ALWAYS);

        Runnable plot = () -> {
            try {
                double xMin = Double.parseDouble(xMinF.getText().trim().replace(",", "."));
                double xMax = Double.parseDouble(xMaxF.getText().trim().replace(",", "."));
                if (xMax <= xMin) return;
                drawPlot(canvas, fnField.getText(), xMin, xMax);
            } catch (Exception ignored) {}
        };
        plotBtn.setOnAction(e -> plot.run());
        fnField.setOnAction(e -> plot.run());
        fnField.textProperty().addListener((o, ov, nv) -> plot.run());
        xMinF.textProperty().addListener((o, ov, nv) -> plot.run());
        xMaxF.textProperty().addListener((o, ov, nv) -> plot.run());
        canvas.widthProperty().addListener((o, ov, nv) -> plot.run());
        canvas.heightProperty().addListener((o, ov, nv) -> plot.run());

        v.getChildren().addAll(row1, row2, wrap);
        Platform.runLater(plot);
        return v;
    }

    private void drawPlot(Canvas canvas, String expr, double xMin, double xMax) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        gc.setFill(Color.web("#1E1F25"));
        gc.fillRect(0, 0, w, h);

        int N = (int) Math.max(50, Math.min(2000, w * 1.5));
        double[] xs = new double[N], ys = new double[N];
        double yMin = Double.POSITIVE_INFINITY, yMax = Double.NEGATIVE_INFINITY;
        Map<String, Double> vars = new HashMap<>(variables);

        boolean parseOk = true;
        for (int i = 0; i < N; i++) {
            double x = xMin + (xMax - xMin) * i / (N - 1);
            xs[i] = x;
            vars.put("x", x);
            double y;
            try {
                y = new Parser(expr, radians, vars).parse();
                if (!Double.isFinite(y)) y = Double.NaN;
            } catch (Exception ex) {
                y = Double.NaN;
                if (i == 0) parseOk = false;
            }
            ys[i] = y;
            if (Double.isFinite(y)) {
                if (y < yMin) yMin = y;
                if (y > yMax) yMax = y;
            }
        }

        if (!parseOk) {
            gc.setFill(Color.web("#E57373"));
            gc.setFont(Font.font("Segoe UI", 12));
            gc.fillText("Espressione non valida", 10, 20);
            return;
        }
        if (!Double.isFinite(yMin) || !Double.isFinite(yMax)) { yMin = -1; yMax = 1; }
        if (yMin == yMax) { yMin -= 1; yMax += 1; }
        double yMargin = (yMax - yMin) * 0.08;
        yMin -= yMargin; yMax += yMargin;

        final double fYMin = yMin, fYMax = yMax;
        java.util.function.DoubleUnaryOperator mapX = x -> (x - xMin) / (xMax - xMin) * w;
        java.util.function.DoubleUnaryOperator mapY = y -> h - (y - fYMin) / (fYMax - fYMin) * h;

        // Grid
        gc.setStroke(Color.web("#2A2B33"));
        gc.setLineWidth(0.5);
        double xStep = niceStep((xMax - xMin) / 8);
        double yStep = niceStep((yMax - yMin) / 6);
        for (double x = Math.ceil(xMin / xStep) * xStep; x <= xMax; x += xStep) {
            double sx = mapX.applyAsDouble(x);
            gc.strokeLine(sx, 0, sx, h);
        }
        for (double y = Math.ceil(yMin / yStep) * yStep; y <= yMax; y += yStep) {
            double sy = mapY.applyAsDouble(y);
            gc.strokeLine(0, sy, w, sy);
        }

        // Axes
        gc.setStroke(Color.web("#9498A3"));
        gc.setLineWidth(1.0);
        if (yMin <= 0 && yMax >= 0) {
            double sy = mapY.applyAsDouble(0.0);
            gc.strokeLine(0, sy, w, sy);
        }
        if (xMin <= 0 && xMax >= 0) {
            double sx = mapX.applyAsDouble(0.0);
            gc.strokeLine(sx, 0, sx, h);
        }

        // Curve
        gc.setStroke(Color.web("#FF7043"));
        gc.setLineWidth(1.6);
        gc.beginPath();
        boolean penDown = false;
        for (int i = 0; i < N; i++) {
            if (!Double.isFinite(ys[i])) { penDown = false; continue; }
            double sx = mapX.applyAsDouble(xs[i]);
            double sy = mapY.applyAsDouble(ys[i]);
            if (sy < -10000 || sy > h + 10000) { penDown = false; continue; }
            if (!penDown) {
                gc.moveTo(sx, sy);
                penDown = true;
            } else {
                gc.lineTo(sx, sy);
            }
        }
        gc.stroke();

        // Range labels
        gc.setFill(Color.web("#6E707A"));
        gc.setFont(Font.font("Segoe UI", 10));
        gc.fillText(String.format(Locale.ROOT, "x ∈ [%.3g, %.3g]", xMin, xMax), 6, 14);
        gc.fillText(String.format(Locale.ROOT, "y ∈ [%.3g, %.3g]", yMin, yMax), 6, 28);
    }

    private static double niceStep(double range) {
        if (range <= 0) return 1;
        double pow = Math.pow(10, Math.floor(Math.log10(range)));
        double f = range / pow;
        if (f < 1.5) return pow;
        if (f < 3)   return 2 * pow;
        if (f < 7)   return 5 * pow;
        return 10 * pow;
    }

    // ===== App icon — drawn programmatically =====
    private Image buildAppIcon(double size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Body — coral rounded square (matches app accent)
        double bgRadius = size * 0.22;
        gc.setFill(Color.web("#FF7043"));
        gc.fillRoundRect(0, 0, size, size, bgRadius, bgRadius);

        // Inner darker bezel
        gc.setStroke(Color.color(0, 0, 0, 0.12));
        gc.setLineWidth(Math.max(0.5, size * 0.012));
        gc.strokeRoundRect(size * 0.06, size * 0.06,
                           size * 0.88, size * 0.88,
                           bgRadius * 0.82, bgRadius * 0.82);

        // Display strip on top
        double dispX = size * 0.16;
        double dispY = size * 0.14;
        double dispW = size * 0.68;
        double dispH = size * 0.22;
        double dispR = size * 0.05;
        gc.setFill(Color.color(0.96, 0.96, 0.98, 1.0));
        gc.fillRoundRect(dispX, dispY, dispW, dispH, dispR, dispR);

        // Button grid 2 rows × 3 cols
        double btnW = size * 0.20;
        double btnH = size * 0.16;
        double btnR = size * 0.045;
        double gap  = size * 0.03;
        double gridW = 3 * btnW + 2 * gap;
        double gridX = (size - gridW) / 2.0;
        double gridY = size * 0.46;

        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                double x = gridX + c * (btnW + gap);
                double y = gridY + r * (btnH + gap);
                if (r == 1 && c == 2) {
                    // Highlighted "=" key — darker for prominence
                    gc.setFill(Color.color(0.10, 0.10, 0.13, 0.92));
                } else {
                    gc.setFill(Color.color(1, 1, 1, 0.90));
                }
                gc.fillRoundRect(x, y, btnW, btnH, btnR, btnR);
            }
        }

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        WritableImage img = new WritableImage((int) size, (int) size);
        canvas.snapshot(sp, img);
        return img;
    }

    public static void main(String[] args) { launch(args); }

    /* ================================================================
     *  Recursive-descent parser — Standard mode (double precision)
     *  with named-variable support.
     * ================================================================ */
    static final class Parser {
        private final String s;
        private final boolean radians;
        private final Map<String, Double> vars;
        private int pos;

        Parser(String s, boolean radians, Map<String, Double> vars) {
            this.s = s; this.radians = radians;
            this.vars = vars != null ? vars : Map.of();
        }

        Parser(String s, boolean radians) { this(s, radians, Map.of()); }

        double parse() {
            double v = parseExpr();
            if (pos < s.length()) throw new RuntimeException("Unexpected '" + s.charAt(pos) + "'");
            return v;
        }

        private char peek() { return pos < s.length() ? s.charAt(pos) : '\0'; }
        private boolean match(char c) { if (peek() == c) { pos++; return true; } return false; }

        private double parseExpr() {
            double v = parseTerm();
            while (true) {
                char c = peek();
                if (c == '+') { pos++; v += parseTerm(); }
                else if (c == '−' || c == '-') { pos++; v -= parseTerm(); }
                else break;
            }
            return v;
        }

        private double parseTerm() {
            double v = parsePct();
            while (true) {
                char c = peek();
                if (c == '×' || c == '*') { pos++; v *= parsePct(); }
                else if (c == '÷' || c == '/') { pos++; v /= parsePct(); }
                else if (isAtomStart(c)) { v *= parsePct(); }
                else break;
            }
            return v;
        }

        private boolean isAtomStart(char c) {
            return Character.isDigit(c) || c == '(' || c == 'π' || c == '√' || Character.isLetter(c);
        }

        private double parsePct() {
            double v = parsePow();
            while (peek() == '%') { pos++; v /= 100.0; }
            return v;
        }

        private double parsePow() {
            double base = parseUnary();
            if (peek() == '^') { pos++; return Math.pow(base, parseUnary()); }
            return base;
        }

        private double parseUnary() {
            if (peek() == '−' || peek() == '-') { pos++; return -parseUnary(); }
            if (peek() == '+') { pos++; return parseUnary(); }
            return parseFact();
        }

        private double parseFact() {
            double v = parseAtom();
            while (peek() == '!') { pos++; v = factorial(v); }
            return v;
        }

        private double parseAtom() {
            char c = peek();
            if (c == '(') {
                pos++;
                double v = parseExpr();
                if (!match(')')) throw new RuntimeException("Missing ')'");
                return v;
            }
            if (c == 'π') { pos++; return Math.PI; }
            if (c == '√') { pos++; return Math.sqrt(parseAtom()); }
            if (Character.isLetter(c)) {
                String name = readName();
                if (vars.containsKey(name) && peek() != '(') return vars.get(name);
                if (name.equals("e") && peek() != '(') return Math.E;
                if (!match('(')) throw new RuntimeException("Expected '(' after " + name);
                double arg = parseExpr();
                if (!match(')')) throw new RuntimeException("Missing ')'");
                return applyFunc(name, arg);
            }
            return readNumber();
        }

        private String readName() {
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) pos++;
            return s.substring(start, pos);
        }

        private double readNumber() {
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new RuntimeException("Expected number");
            // Optional scientific exponent: 'e'|'E' [+|-] digits...
            // Probe ahead — only consume if exponent is actually present, otherwise leave 'e' for the
            // outer parser to interpret as Math.E / variable.
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                int probe = pos + 1;
                boolean negExp = false;
                if (probe < s.length() && (s.charAt(probe) == '+' || s.charAt(probe) == '-' || s.charAt(probe) == '−')) {
                    negExp = (s.charAt(probe) != '+');
                    probe++;
                }
                if (probe < s.length() && Character.isDigit(s.charAt(probe))) {
                    int expStart = probe;
                    while (probe < s.length() && Character.isDigit(s.charAt(probe))) probe++;
                    // Reconstruct as standard ASCII for Double.parseDouble
                    String mantissa = s.substring(start, pos);
                    String exp = s.substring(expStart, probe);
                    pos = probe;
                    return Double.parseDouble(mantissa + "e" + (negExp ? "-" : "") + exp);
                }
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private double applyFunc(String n, double x) {
            return switch (n) {
                case "sin"  -> Math.sin(radians ? x : Math.toRadians(x));
                case "cos"  -> Math.cos(radians ? x : Math.toRadians(x));
                case "tan"  -> Math.tan(radians ? x : Math.toRadians(x));
                case "asin" -> radians ? Math.asin(x) : Math.toDegrees(Math.asin(x));
                case "acos" -> radians ? Math.acos(x) : Math.toDegrees(Math.acos(x));
                case "atan" -> radians ? Math.atan(x) : Math.toDegrees(Math.atan(x));
                case "ln"   -> Math.log(x);
                case "log"  -> Math.log10(x);
                case "exp"  -> Math.exp(x);
                case "sqrt" -> Math.sqrt(x);
                case "abs"  -> Math.abs(x);
                default     -> throw new RuntimeException("Unknown: " + n);
            };
        }

        private double factorial(double v) {
            if (v < 0 || v != Math.floor(v) || v > 170) throw new RuntimeException("Bad factorial");
            double r = 1;
            for (int i = 2; i <= (long) v; i++) r *= i;
            return r;
        }
    }

    /* ================================================================
     *  Programmer-mode parser — BigInteger, base-aware
     * ================================================================ */
    static final class ProgParser {
        private final String s;
        private final int base;
        private int pos;

        ProgParser(String s, int base) { this.s = s; this.base = base; }

        BigInteger parse() {
            BigInteger v = parseOr();
            skipWs();
            if (pos < s.length()) throw new RuntimeException("Unexpected '" + s.charAt(pos) + "'");
            return v;
        }

        private void skipWs() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }

        private boolean match(char c) {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; }
            return false;
        }

        private boolean matchStr(String w) {
            skipWs();
            if (s.startsWith(w, pos)) { pos += w.length(); return true; }
            return false;
        }

        private boolean matchWord(String w) {
            skipWs();
            if (s.regionMatches(pos, w, 0, w.length())) {
                int end = pos + w.length();
                if (end < s.length() && isDigitForBase(s.charAt(end))) return false;
                pos = end;
                return true;
            }
            return false;
        }

        private boolean isDigitForBase(char c) {
            return switch (base) {
                case 16 -> (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
                case 10 -> c >= '0' && c <= '9';
                case 8  -> c >= '0' && c <= '7';
                case 2  -> c == '0' || c == '1';
                default -> false;
            };
        }

        private BigInteger parseOr()   { BigInteger v = parseXor();   while (matchWord("OR"))  v = v.or(parseXor());    return v; }
        private BigInteger parseXor()  { BigInteger v = parseAnd();   while (matchWord("XOR")) v = v.xor(parseAnd());   return v; }
        private BigInteger parseAnd()  { BigInteger v = parseShift(); while (matchWord("AND")) v = v.and(parseShift()); return v; }

        private BigInteger parseShift() {
            BigInteger v = parseAdd();
            while (true) {
                if (matchStr("<<"))      v = v.shiftLeft(parseAdd().intValueExact());
                else if (matchStr(">>")) v = v.shiftRight(parseAdd().intValueExact());
                else break;
            }
            return v;
        }

        private BigInteger parseAdd() {
            BigInteger v = parseMul();
            while (true) {
                skipWs();
                if (match('+')) v = v.add(parseMul());
                else if (match('-') || match('−')) v = v.subtract(parseMul());
                else break;
            }
            return v;
        }

        private BigInteger parseMul() {
            BigInteger v = parseUnary();
            while (true) {
                skipWs();
                if (match('×') || match('*')) v = v.multiply(parseUnary());
                else if (match('÷') || match('/')) v = v.divide(parseUnary());
                else break;
            }
            return v;
        }

        private BigInteger parseUnary() {
            if (matchWord("NOT")) return parseUnary().not();
            skipWs();
            if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '−')) {
                pos++;
                return parseUnary().negate();
            }
            return parseAtom();
        }

        private BigInteger parseAtom() {
            skipWs();
            if (match('(')) {
                BigInteger v = parseOr();
                if (!match(')')) throw new RuntimeException("Missing ')'");
                return v;
            }
            int start = pos;
            while (pos < s.length() && isDigitForBase(s.charAt(pos))) pos++;
            if (start == pos) throw new RuntimeException("Expected number");
            return new BigInteger(s.substring(start, pos), base);
        }
    }
}
