package calc;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleSupplier;

public class Toolkit {

    public interface Bridge {
        void insert(String s);
        void setVariable(String name, double value);
        Map<String, Double> variables();
        void removeVariable(String name);
        boolean radians();
        void addVariableChangeListener(Runnable r);
    }

    private final Bridge bridge;
    private final Runnable closeAction;
    private final Runnable backAction;
    private final java.util.LinkedHashMap<String, Node> sectionPanes = new java.util.LinkedHashMap<>();
    private Label subtitleLabel;
    private VBox contentHolder;

    // ---- Sezioni sub-carousel state ----
    private static final String[] SEZ_NAMES = { "Profili", "Piatti e sezioni", "Tubi" };
    private int sezioniIdx = 1; // start centered on "Piatti e sezioni"
    private final Label[] carouselLabels = new Label[3];
    private HBox carouselBox;
    private javafx.scene.layout.StackPane subtitleHost;
    private javafx.scene.layout.StackPane sezContentHost;
    private final Node[] sezSubPanes = new Node[3];
    private boolean carouselAnimating = false;

    public Toolkit(Bridge bridge, Runnable closeAction, Runnable backAction) {
        this.bridge = bridge;
        this.closeAction = closeAction;
        this.backAction = backAction;
    }

    /** Backward-compat constructor (no back button). */
    public Toolkit(Bridge bridge, Runnable closeAction) {
        this(bridge, closeAction, null);
    }

    /** Show the section by visible name (e.g. "Materiali"). */
    public void selectTab(String name) {
        if (sectionPanes.isEmpty()) return;
        // case-insensitive lookup
        String resolved = null;
        for (String key : sectionPanes.keySet()) {
            if (key.equalsIgnoreCase(name)) { resolved = key; break; }
        }
        if (resolved == null) resolved = sectionPanes.keySet().iterator().next();
        Node pane = sectionPanes.get(resolved);
        contentHolder.getChildren().setAll(pane);
        VBox.setVgrow(pane, Priority.ALWAYS);

        // Swap subtitle: carousel for "Sezioni", plain label otherwise
        subtitleHost.getChildren().clear();
        if ("Sezioni".equalsIgnoreCase(resolved)) {
            subtitleHost.getChildren().add(carouselBox);
            updateCarousel();
        } else {
            subtitleLabel.setText(resolved);
            subtitleHost.getChildren().add(subtitleLabel);
        }
    }

    // ====================== Sezioni carousel ======================
    private HBox buildCarousel() {
        HBox c = new HBox(4);
        c.setAlignment(Pos.CENTER);
        c.getStyleClass().add("toolkit-carousel");
        for (int i = 0; i < 3; i++) {
            Label l = new Label("");
            carouselLabels[i] = l;
            l.getStyleClass().add(i == 1 ? "carousel-center" : "carousel-side");
            final int delta = i - 1; // -1 left, 0 center, +1 right
            l.setOnMouseClicked(e -> {
                if (delta == 0) return;
                int target = delta > 0 ? nextSezIdx() : prevSezIdx();
                goToSezioni(target, delta);
            });
            c.getChildren().add(l);
        }
        return c;
    }

    private int prevSezIdx() { return (sezioniIdx - 1 + SEZ_NAMES.length) % SEZ_NAMES.length; }
    private int nextSezIdx() { return (sezioniIdx + 1) % SEZ_NAMES.length; }

    private void updateCarousel() {
        carouselLabels[0].setText(SEZ_NAMES[prevSezIdx()]);
        carouselLabels[1].setText(SEZ_NAMES[sezioniIdx]);
        carouselLabels[2].setText(SEZ_NAMES[nextSezIdx()]);
    }

    private void goToSezioni(int newIdx, int direction) {
        if (newIdx == sezioniIdx) return;
        if (carouselAnimating) return;
        if (sezContentHost == null || sezSubPanes[newIdx] == null) return;
        carouselAnimating = true;

        int dir = direction;
        Node oldPane = sezContentHost.getChildren().isEmpty() ? null : sezContentHost.getChildren().get(0);
        Node newPane = sezSubPanes[newIdx];
        double w = sezContentHost.getWidth();
        if (w <= 0) w = 320;

        sezContentHost.getChildren().add(newPane);
        newPane.setTranslateX(dir * w);
        newPane.setOpacity(0);

        javafx.animation.TranslateTransition tIn =
            new javafx.animation.TranslateTransition(javafx.util.Duration.millis(280), newPane);
        tIn.setFromX(dir * w); tIn.setToX(0);
        javafx.animation.FadeTransition fIn =
            new javafx.animation.FadeTransition(javafx.util.Duration.millis(280), newPane);
        fIn.setFromValue(0); fIn.setToValue(1);

        javafx.animation.ParallelTransition pt;
        if (oldPane != null) {
            javafx.animation.TranslateTransition tOut =
                new javafx.animation.TranslateTransition(javafx.util.Duration.millis(280), oldPane);
            tOut.setFromX(0); tOut.setToX(-dir * w);
            javafx.animation.FadeTransition fOut =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(280), oldPane);
            fOut.setFromValue(1); fOut.setToValue(0);
            pt = new javafx.animation.ParallelTransition(tOut, fOut, tIn, fIn);
        } else {
            pt = new javafx.animation.ParallelTransition(tIn, fIn);
        }
        final Node oldRef = oldPane;
        pt.setOnFinished(e -> {
            if (oldRef != null) {
                sezContentHost.getChildren().remove(oldRef);
                oldRef.setTranslateX(0);
                oldRef.setOpacity(1);
            }
            carouselAnimating = false;
        });
        sezioniIdx = newIdx;
        updateCarousel();
        pt.play();
    }

    public Node buildPanel() {
        // ---- Header: ← (back) + Title/Subtitle centered + ✕ (close) ----
        Button back = new Button("←");
        back.getStyleClass().addAll("chip", "icon", "toolkit-back");
        back.setFocusTraversable(false);
        back.setOnAction(e -> { if (backAction != null) backAction.run(); else closeAction.run(); });

        Label title = new Label("Strumenti meccanici");
        title.getStyleClass().add("toolkit-main-title");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);

        subtitleLabel = new Label("");
        subtitleLabel.getStyleClass().add("toolkit-subtitle");
        subtitleLabel.setMaxWidth(Double.MAX_VALUE);
        subtitleLabel.setAlignment(Pos.CENTER);

        carouselBox = buildCarousel();

        subtitleHost = new javafx.scene.layout.StackPane();
        subtitleHost.setAlignment(Pos.CENTER);
        subtitleHost.getChildren().add(subtitleLabel);

        VBox titleBox = new VBox(2, title, subtitleHost);
        titleBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button close = new Button("✕");
        close.getStyleClass().addAll("chip", "icon");
        close.setFocusTraversable(false);
        close.setOnAction(e -> closeAction.run());

        HBox header = new HBox(8, back, titleBox, close);
        header.setAlignment(Pos.CENTER);

        // ---- Section panes (built once, reused) ----
        sectionPanes.put("Unità",     buildUnitsTab().getContent());
        sectionPanes.put("Materiali", buildMaterialsTab().getContent());
        sectionPanes.put("Sezioni",   buildSectionsTab().getContent());
        sectionPanes.put("Formule",   buildFormulasTab().getContent());
        sectionPanes.put("Procedure", buildWizardsTab().getContent());
        sectionPanes.put("Strumenti", buildStrumentiTab().getContent());

        contentHolder = new VBox();
        contentHolder.setFillWidth(true);
        VBox.setVgrow(contentHolder, Priority.ALWAYS);

        // Default to first section
        selectTab("Unità");

        VBox panel = new VBox(10, header, contentHolder);
        panel.getStyleClass().add("history-panel");
        panel.setPadding(new Insets(14));
        return panel;
    }

    // ====================== UNIT CONVERTER ======================
    private Tab buildUnitsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12, 4, 12, 4));

        box.getChildren().add(description(
            "Converti tra 14 categorie di unità (lunghezza, massa, forza, pressione, "
            + "coppia, energia, potenza, velocità, area, volume, angolo, frequenza, "
            + "densità e temperatura con offset). Scegli categoria → unità sorgente → "
            + "valore → unità destinazione. Click 'Inserisci' per copiare il risultato nel calcolatore."));

        ComboBox<Catalog.UnitCategory> catCombo = new ComboBox<>(FXCollections.observableArrayList(Catalog.CATEGORIES));
        catCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Catalog.UnitCategory c) { return c == null ? "" : c.name(); }
            @Override public Catalog.UnitCategory fromString(String s) { return null; }
        });
        catCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Catalog.UnitDef> fromCombo = new ComboBox<>();
        ComboBox<Catalog.UnitDef> toCombo = new ComboBox<>();
        StringConverter<Catalog.UnitDef> uConv = new StringConverter<>() {
            @Override public String toString(Catalog.UnitDef u) { return u == null ? "" : u.symbol() + " — " + u.name(); }
            @Override public Catalog.UnitDef fromString(String s) { return null; }
        };
        fromCombo.setConverter(uConv);
        toCombo.setConverter(uConv);
        fromCombo.setMaxWidth(Double.MAX_VALUE);
        toCombo.setMaxWidth(Double.MAX_VALUE);

        TextField valueField = numField();
        valueField.setText("1");
        Label result = new Label("—");
        result.getStyleClass().add("toolkit-result");

        Runnable doConvert = () -> {
            Catalog.UnitCategory cat = catCombo.getValue();
            Catalog.UnitDef from = fromCombo.getValue();
            Catalog.UnitDef to = toCombo.getValue();
            if (cat == null || from == null || to == null) { result.setText("—"); return; }
            try {
                double v = parseDouble(valueField.getText());
                double r = Catalog.convert(cat, from, to, v);
                result.setText(fmt(r) + " " + to.symbol());
            } catch (Exception ex) {
                result.setText("input non valido");
            }
        };

        catCombo.valueProperty().addListener((o, ov, nv) -> {
            if (nv == null) return;
            fromCombo.setItems(FXCollections.observableArrayList(nv.units()));
            toCombo.setItems(FXCollections.observableArrayList(nv.units()));
            fromCombo.setValue(nv.units().get(0));
            toCombo.setValue(nv.units().size() > 1 ? nv.units().get(1) : nv.units().get(0));
            doConvert.run();
        });
        fromCombo.valueProperty().addListener((o, ov, nv) -> doConvert.run());
        toCombo.valueProperty().addListener((o, ov, nv) -> doConvert.run());
        valueField.textProperty().addListener((o, ov, nv) -> doConvert.run());

        catCombo.getSelectionModel().select(0);

        Button insBtn = new Button("Inserisci valore convertito");
        insBtn.getStyleClass().add("chip");
        insBtn.setMaxWidth(Double.MAX_VALUE);
        insBtn.setOnAction(e -> {
            try {
                double v = parseDouble(valueField.getText());
                double r = Catalog.convert(catCombo.getValue(), fromCombo.getValue(), toCombo.getValue(), v);
                bridge.insert(fmt(r));
            } catch (Exception ignored) {}
        });

        box.getChildren().addAll(
            section("Categoria"), catCombo,
            section("Da"),        fromCombo,
            section("Valore"),    valueField,
            section("A"),         toCombo,
            section("Risultato"), result, insBtn
        );

        Tab t = new Tab("Unità", scroll(box));
        return t;
    }

    // ====================== MATERIALS ======================
    private Tab buildMaterialsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12, 4, 12, 4));

        box.getChildren().add(description(
            "16 materiali ingegneristici con proprietà fisiche: densità ρ, moduli elastici E e G, "
            + "coefficiente di Poisson ν, snervamento σy, rottura σt, dilatazione termica α. "
            + "Click 'Carica tutte' importa le 7 grandezze come variabili (ro, E, G, nu, sy, su, alpha) "
            + "richiamabili direttamente nelle formule del calcolatore."));

        TextField search = new TextField();
        search.setPromptText("Cerca materiale… (es. acciaio, 316, Al)");
        search.getStyleClass().add("toolkit-input");
        search.setMaxWidth(Double.MAX_VALUE);

        FilteredList<Catalog.Material> filtered =
            new FilteredList<>(FXCollections.observableArrayList(Catalog.MATERIALS), m -> true);
        ListView<Catalog.Material> list = new ListView<>(filtered);
        list.setPrefHeight(150);
        list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Catalog.Material m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? null : m.name());
            }
        });

        search.textProperty().addListener((o, ov, nv) -> {
            String q = nv == null ? "" : nv.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(q.isEmpty() ? m -> true
                : m -> m.name().toLowerCase(Locale.ROOT).contains(q));
            if (!filtered.isEmpty() && list.getSelectionModel().getSelectedItem() == null) {
                list.getSelectionModel().select(0);
            }
        });

        GridPane props = new GridPane();
        props.setHgap(6); props.setVgap(4);
        props.getStyleClass().add("toolkit-table");
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(c == 0 ? 28 : c == 1 ? 42 : 30);
            props.getColumnConstraints().add(cc);
        }

        Button loadAll = new Button("Carica tutte come variabili (ro, E, G, nu, sy, su, alpha)");
        loadAll.getStyleClass().add("chip");
        loadAll.setMaxWidth(Double.MAX_VALUE);
        loadAll.setDisable(true);

        list.getSelectionModel().selectedItemProperty().addListener((o, ov, m) -> {
            props.getChildren().clear();
            if (m == null) { loadAll.setDisable(true); return; }
            loadAll.setDisable(false);
            addPropertyRow(props, 0, "ρ",  fmt(m.rho())            + " kg/m³",  "ro",    m.rho());
            addPropertyRow(props, 1, "E",  fmt(m.E())              + " MPa",    "E",     m.E());
            addPropertyRow(props, 2, "G",  fmt(m.G())              + " MPa",    "G",     m.G());
            addPropertyRow(props, 3, "ν",  fmt(m.nu()),                          "nu",    m.nu());
            addPropertyRow(props, 4, "σy", fmt(m.yieldStress())    + " MPa",    "sy",    m.yieldStress());
            addPropertyRow(props, 5, "σt", fmt(m.tensileStress())  + " MPa",    "su",    m.tensileStress());
            addPropertyRow(props, 6, "α",  fmt(m.alpha())          + " ×10⁻⁶/K","alpha", m.alpha());
        });

        loadAll.setOnAction(e -> {
            Catalog.Material m = list.getSelectionModel().getSelectedItem();
            if (m == null) return;
            bridge.setVariable("ro",    m.rho());
            bridge.setVariable("E",     m.E());
            bridge.setVariable("G",     m.G());
            bridge.setVariable("nu",    m.nu());
            bridge.setVariable("sy",    m.yieldStress());
            bridge.setVariable("su",    m.tensileStress());
            bridge.setVariable("alpha", m.alpha());
        });

        list.getSelectionModel().select(0);

        box.getChildren().addAll(
            section("Cerca"), search,
            section("Materiali"), list,
            section("Proprietà"), props,
            loadAll
        );
        return new Tab("Materiali", scroll(box));
    }

    private void addPropertyRow(GridPane g, int row, String sym, String val, String varName, double rawValue) {
        Label l1 = new Label(sym);
        l1.getStyleClass().add("toolkit-key");
        Label l2 = new Label(val);
        l2.getStyleClass().add("toolkit-val");
        HBox btns = new HBox(4);
        Button ins = new Button("Ins");
        Button var = new Button("→ " + varName);
        ins.getStyleClass().add("chip");
        var.getStyleClass().add("chip");
        ins.setOnAction(e -> bridge.insert(fmt(rawValue)));
        var.setOnAction(e -> bridge.setVariable(varName, rawValue));
        btns.getChildren().addAll(ins, var);
        g.add(l1, 0, row);
        g.add(l2, 1, row);
        g.add(btns, 2, row);
    }

    // ====================== SECTIONS ======================
    /** mm | cm | m — used as state for both parametric and commercial section displays. */
    private final javafx.beans.property.StringProperty sectionUnit =
        new javafx.beans.property.SimpleStringProperty("mm");

    /** Currently-selected material (drives weight calculations). null = none. */
    private final javafx.beans.property.ObjectProperty<Catalog.Material> sectionMaterial =
        new javafx.beans.property.SimpleObjectProperty<>(null);

    private Tab buildSectionsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12, 4, 12, 4));

        // ---- Shared unit selector ----
        ComboBox<String> unitCombo = new ComboBox<>(FXCollections.observableArrayList("mm", "cm", "m"));
        unitCombo.setValue("mm");
        unitCombo.setMaxWidth(Double.MAX_VALUE);
        sectionUnit.bind(unitCombo.valueProperty());

        // ---- Shared material selector ----
        ObservableList<Catalog.Material> matOptions = FXCollections.observableArrayList();
        matOptions.add(null);
        matOptions.addAll(Catalog.MATERIALS);
        ComboBox<Catalog.Material> matCombo = new ComboBox<>(matOptions);
        matCombo.setMaxWidth(Double.MAX_VALUE);
        matCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Catalog.Material m) { return m == null ? "— nessuno (no peso) —" : m.name(); }
            @Override public Catalog.Material fromString(String s) { return null; }
        });
        matCombo.getSelectionModel().select(0);
        sectionMaterial.bind(matCombo.valueProperty());

        box.getChildren().addAll(
            section("Unità di misura"), unitCombo,
            section("Materiale (per peso)"), matCombo);

        // ---- Build 3 sub-panes ----
        sezSubPanes[0] = buildCommercialProfilesPanel(); // Profili
        sezSubPanes[1] = buildParametricSectionsPane(); // Piatti e sezioni
        sezSubPanes[2] = buildPipesPanel();              // Tubi

        sezContentHost = new javafx.scene.layout.StackPane();
        sezContentHost.getChildren().add(sezSubPanes[sezioniIdx]);
        sezContentHost.setMinHeight(280);
        // Clip overflow during slide animation
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(sezContentHost.widthProperty());
        clip.heightProperty().bind(sezContentHost.heightProperty());
        sezContentHost.setClip(clip);

        box.getChildren().add(sezContentHost);
        return new Tab("Sezioni", scroll(box));
    }

    private VBox buildParametricSectionsPane() {
        VBox box = new VBox(8);
        box.getChildren().add(description(
            "Forme parametriche: rettangolo, rettangolo cavo, cerchio pieno, tubo, profilo T, "
            + "profilo L equilatero (sezioni 2D → A, Ix, Iy, Wx, Wy, raggi giraz., perimetro, "
            + "baricentro). Più 'Piatto / Lamiera 3D' con spessore → area superficie, volume, "
            + "massa. Con materiale selezionato calcola anche il peso lineare kg/m."));

        ComboBox<Sections.Shape> shape = new ComboBox<>(FXCollections.observableArrayList(Sections.Shape.values()));
        shape.setMaxWidth(Double.MAX_VALUE);
        shape.setConverter(new StringConverter<>() {
            @Override public String toString(Sections.Shape s) {
                if (s == null) return "";
                return switch (s) {
                    case RETTANGOLO -> "Rettangolo (b × h)";
                    case RETTANGOLO_CAVO -> "Rettangolo cavo (b × h × t)";
                    case CERCHIO -> "Cerchio pieno (D)";
                    case TUBO -> "Tubo (De × t)";
                    case PROFILO_T -> "Profilo T (b × h × tf × tw)";
                    case PROFILO_L -> "Profilo L equilatero (a × t)";
                    case PIATTO   -> "Piatto / Lamiera 3D (b × h × s)";
                };
            }
            @Override public Sections.Shape fromString(String s) { return null; }
        });

        GridPane inputs = new GridPane();
        inputs.setHgap(6); inputs.setVgap(4);
        for (int c = 0; c < 4; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            inputs.getColumnConstraints().add(cc);
        }
        TextField[] fields = new TextField[4];
        Label[] labels = new Label[4];
        for (int i = 0; i < 4; i++) {
            labels[i] = new Label("");
            labels[i].getStyleClass().add("toolkit-key");
            fields[i] = numField();
            fields[i].setMaxWidth(Double.MAX_VALUE);
            inputs.add(labels[i], 0, i);
            inputs.add(fields[i], 1, i, 3, 1);
        }

        GridPane resultsGrid = new GridPane();
        resultsGrid.setHgap(6); resultsGrid.setVgap(4);
        resultsGrid.getStyleClass().add("toolkit-table");
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(c == 0 ? 28 : c == 1 ? 42 : 30);
            resultsGrid.getColumnConstraints().add(cc);
        }

        Button calc = new Button("Calcola proprietà");
        calc.getStyleClass().add("eq-chip");
        calc.setMaxWidth(Double.MAX_VALUE);

        Label hint = hint("");

        Runnable updateInputs = () -> {
            Sections.Shape s = shape.getValue();
            if (s == null) return;
            for (int i = 0; i < 4; i++) { labels[i].setText(""); fields[i].setVisible(false); fields[i].setText(""); }
            String[] names = switch (s) {
                case RETTANGOLO       -> new String[]{"b [mm]", "h [mm]"};
                case RETTANGOLO_CAVO  -> new String[]{"b [mm]", "h [mm]", "t [mm]"};
                case CERCHIO          -> new String[]{"D [mm]"};
                case TUBO             -> new String[]{"De [mm]", "t [mm]"};
                case PROFILO_T        -> new String[]{"b [mm]", "h [mm]", "tf [mm]", "tw [mm]"};
                case PROFILO_L        -> new String[]{"a [mm]", "t [mm]"};
                case PIATTO           -> new String[]{"b [mm]", "h [mm]", "s [mm]"};
            };
            for (int i = 0; i < names.length; i++) {
                labels[i].setText(names[i]);
                fields[i].setVisible(true);
            }
            hint.setText(s == Sections.Shape.PIATTO
                ? "Lastra 3D: area superficie, volume, massa (con materiale)."
                : "Sezione trasversale: A, I, W, baricentro. Con materiale: peso lineare kg/m.");
        };
        shape.valueProperty().addListener((o, ov, nv) -> updateInputs.run());
        shape.getSelectionModel().select(0);

        final Object[] last = { null };
        final Sections.Shape[] lastShape = { null };
        Runnable render = () -> {
            resultsGrid.getChildren().clear();
            if (last[0] == null) return;
            Catalog.Material mat = sectionMaterial.get();
            if (last[0] instanceof Sections.PlateResult pr) {
                renderPlateResultRows(resultsGrid, pr, sectionUnit.get(), mat);
            } else if (last[0] instanceof Sections.SectionResult sr) {
                renderSectionResultRows(resultsGrid, sr, sectionUnit.get(), mat);
            }
        };

        calc.setOnAction(e -> {
            try {
                lastShape[0] = shape.getValue();
                if (lastShape[0] == Sections.Shape.PIATTO) {
                    last[0] = Sections.plate(parseDouble(fields[0].getText()),
                                              parseDouble(fields[1].getText()),
                                              parseDouble(fields[2].getText()));
                } else {
                    last[0] = computeShape(lastShape[0], fields);
                }
                render.run();
            } catch (Exception ex) {
                resultsGrid.getChildren().clear();
                Label err = new Label("Errore: " + ex.getMessage());
                err.getStyleClass().add("toolkit-err");
                resultsGrid.add(err, 0, 0, 3, 1);
            }
        });

        sectionUnit.addListener((o, ov, nv) -> render.run());
        sectionMaterial.addListener((o, ov, nv) -> render.run());

        box.getChildren().addAll(
            section("Forma"), shape, hint, inputs, calc,
            section("Risultati"), resultsGrid);
        return box;
    }

    /** Render section result rows with scaling + optional material weight. */
    private void renderSectionResultRows(GridPane grid, Sections.SectionResult r, String linearUnit, Catalog.Material mat) {
        int row = 0;
        for (var entry : r.asMap().entrySet()) {
            String key = entry.getKey();
            double valMm = entry.getValue();
            double scaled = scaleSectionValue(key, valMm, linearUnit);
            String unitStr = sectionUnitStr(key, linearUnit);
            String label = switch (key) {
                case "yc" -> "baricentro yc";
                case "xc" -> "baricentro xc";
                default   -> key;
            };
            addPropertyRow(grid, row++, label,
                fmt(scaled) + (unitStr.isEmpty() ? "" : " " + unitStr),
                key, scaled);
        }
        // Linear weight kg/m = A [m²] × ρ [kg/m³]
        if (mat != null) {
            double a_m2 = r.A() * 1e-6;
            double kgPerM = a_m2 * mat.rho();
            addPropertyRow(grid, row++, "kg/m  (" + mat.name() + ")",
                fmt(kgPerM) + " kg/m", "kg_per_m", kgPerM);
        }
    }

    /** Render plate (3D body) result rows. */
    private void renderPlateResultRows(GridPane grid, Sections.PlateResult r, String linearUnit, Catalog.Material mat) {
        int row = 0;
        // Surface area
        double surface = scaleSectionValue("A", r.surface(), linearUnit);
        addPropertyRow(grid, row++, "area superficie",
            fmt(surface) + " " + linearUnit + "²", "surface", surface);
        // Volume: scale by linearUnit³
        double volFactor = linearUnit.equals("mm") ? 1.0 : linearUnit.equals("cm") ? 1e-3 : 1e-9;
        double vol = r.volume() * volFactor;
        addPropertyRow(grid, row++, "volume",
            fmt(vol) + " " + linearUnit + "³", "volume", vol);
        // Thickness in linear unit
        double thk = scaleSectionValue("s", r.thickness(), linearUnit);
        addPropertyRow(grid, row++, "spessore", fmt(thk) + " " + linearUnit, "s", thk);
        // Mass (if material)
        if (mat != null) {
            double vol_m3 = r.volume() * 1e-9;
            double mass = vol_m3 * mat.rho();
            addPropertyRow(grid, row++, "massa  (" + mat.name() + ")",
                fmt(mass) + " kg", "massa", mass);
        }
    }

    /** Scale a section property value from mm-based to selected linear unit. */
    private double scaleSectionValue(String prop, double v_mm, String u) {
        // Determine exponent of length for this property
        int exp;
        switch (prop) {
            case "A":  exp = 2; break;
            case "Ix": case "Iy": exp = 4; break;
            case "Wx": case "Wy": exp = 3; break;
            default: exp = 1; // perimetro, ix, iy, yc, xc
        }
        double base = u.equals("mm") ? 1.0 : u.equals("cm") ? 0.1 : 0.001;
        return v_mm * Math.pow(base, exp);
    }

    private String sectionUnitStr(String prop, String linearUnit) {
        switch (prop) {
            case "A":  return linearUnit + "²";
            case "Ix": case "Iy": return linearUnit + "⁴";
            case "Wx": case "Wy": return linearUnit + "³";
            default: return linearUnit;
        }
    }

    private VBox buildCommercialProfilesPanel() {
        VBox v = new VBox(6);
        v.getChildren().add(description(
            "Profili strutturali normalizzati IPE (a I), HEA / HEB (a H), UPN (a U) con "
            + "dimensioni h/b/tw/tf, area A, momenti d'inerzia Ix/Iy, moduli Wx/Wy e peso "
            + "kg/m (catalogo acciaio ρ=7850). Inserisci la lunghezza in metri per il peso "
            + "totale del tratto."));
        v.getChildren().add(section("Profili commerciali (IPE/HEA/HEB/UPN)"));

        ComboBox<Catalog.ProfileSeries> seriesCombo = new ComboBox<>(FXCollections.observableArrayList(Catalog.PROFILE_SERIES));
        seriesCombo.setMaxWidth(Double.MAX_VALUE);
        seriesCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Catalog.ProfileSeries s) { return s == null ? "" : s.name(); }
            @Override public Catalog.ProfileSeries fromString(String s) { return null; }
        });

        ComboBox<Catalog.Profile> sizeCombo = new ComboBox<>();
        sizeCombo.setMaxWidth(Double.MAX_VALUE);
        sizeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Catalog.Profile p) { return p == null ? "" : p.designation(); }
            @Override public Catalog.Profile fromString(String s) { return null; }
        });

        // Length [m] input
        TextField lenField = numField();
        lenField.setText("1.0");
        lenField.setPromptText("Lunghezza profilo [m]");

        GridPane out = new GridPane();
        out.setHgap(6); out.setVgap(4);
        out.getStyleClass().add("toolkit-table");
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(c == 0 ? 28 : c == 1 ? 42 : 30);
            out.getColumnConstraints().add(cc);
        }

        Runnable update = () -> {
            out.getChildren().clear();
            Catalog.Profile p = sizeCombo.getValue();
            if (p == null) return;
            String u = sectionUnit.get();
            // Dimensions (linear)
            addPropertyRow(out, 0, "h",  fmt(scaleSectionValue("h",  p.h(),  u)) + " " + u, "h",  scaleSectionValue("h",  p.h(),  u));
            addPropertyRow(out, 1, "b",  fmt(scaleSectionValue("b",  p.b(),  u)) + " " + u, "b",  scaleSectionValue("b",  p.b(),  u));
            addPropertyRow(out, 2, "tw", fmt(scaleSectionValue("tw", p.tw(), u)) + " " + u, "tw", scaleSectionValue("tw", p.tw(), u));
            addPropertyRow(out, 3, "tf", fmt(scaleSectionValue("tf", p.tf(), u)) + " " + u, "tf", scaleSectionValue("tf", p.tf(), u));
            // Area / inertia / section modulus (scaled)
            double aS  = scaleSectionValue("A",  p.A(),  u);
            double ixS = scaleSectionValue("Ix", p.Ix(), u);
            double iyS = scaleSectionValue("Iy", p.Iy(), u);
            double wxS = scaleSectionValue("Wx", p.Wx(), u);
            double wyS = scaleSectionValue("Wy", p.Wy(), u);
            addPropertyRow(out, 4, "A",  fmt(aS)  + " " + u + "²", "A",  aS);
            addPropertyRow(out, 5, "Ix", fmt(ixS) + " " + u + "⁴", "Ix", ixS);
            addPropertyRow(out, 6, "Iy", fmt(iyS) + " " + u + "⁴", "Iy", iyS);
            addPropertyRow(out, 7, "Wx", fmt(wxS) + " " + u + "³", "Wx", wxS);
            addPropertyRow(out, 8, "Wy", fmt(wyS) + " " + u + "³", "Wy", wyS);
            // Weight per meter (always kg/m, doesn't scale). Profile catalog kg/m assumes steel ρ=7850.
            int r2 = 9;
            addPropertyRow(out, r2++, "kg/m (acciaio)", fmt(p.weight()), "kg_per_m", p.weight());
            // If user picked a different material, recompute kg/m from area × rho
            Catalog.Material mat = sectionMaterial.get();
            if (mat != null && Math.abs(mat.rho() - 7850) > 1) {
                double altKgPerM = (p.A() * 1e-6) * mat.rho();
                addPropertyRow(out, r2++, "kg/m (" + mat.name() + ")",
                    fmt(altKgPerM), "kg_per_m_mat", altKgPerM);
            }
            // Total weight = weight × length
            double L;
            try { L = Double.parseDouble(lenField.getText().replace(",", ".").trim()); }
            catch (Exception e) { L = Double.NaN; }
            if (Double.isFinite(L) && L > 0) {
                double weightUsed = (mat != null) ? (p.A() * 1e-6) * mat.rho() : p.weight();
                double totKg = weightUsed * L;
                addPropertyRow(out, r2++, "peso tot", fmt(totKg) + " kg (L=" + fmt(L) + " m)",
                    "peso_tot", totKg);
            }
        };

        seriesCombo.valueProperty().addListener((o, ov, nv) -> {
            if (nv == null) return;
            sizeCombo.setItems(FXCollections.observableArrayList(nv.profiles()));
            sizeCombo.setValue(nv.profiles().get(0));
        });
        sizeCombo.valueProperty().addListener((o, ov, nv) -> update.run());
        lenField.textProperty().addListener((o, ov, nv) -> update.run());
        sectionUnit.addListener((o, ov, nv) -> update.run());
        seriesCombo.getSelectionModel().select(0);

        v.getChildren().addAll(seriesCombo, sizeCombo,
            section("Lunghezza [m] (per peso totale)"), lenField, out);
        return v;
    }

    private Sections.SectionResult computeShape(Sections.Shape s, TextField[] f) {
        double v0 = parseDouble(f[0].getText());
        return switch (s) {
            case RETTANGOLO -> Sections.rectangle(v0, parseDouble(f[1].getText()));
            case RETTANGOLO_CAVO -> Sections.hollowRect(v0, parseDouble(f[1].getText()), parseDouble(f[2].getText()));
            case CERCHIO -> Sections.solidCircle(v0);
            case TUBO -> Sections.tube(v0, parseDouble(f[1].getText()));
            case PROFILO_T -> Sections.tProfile(v0, parseDouble(f[1].getText()), parseDouble(f[2].getText()), parseDouble(f[3].getText()));
            case PROFILO_L -> Sections.lProfile(v0, parseDouble(f[1].getText()));
            case PIATTO    -> throw new IllegalStateException("PIATTO va gestito separatamente (plate())");
        };
    }

    private String unitForSectionKey(String k) {
        return switch (k) {
            case "A" -> "mm²";
            case "Ix", "Iy" -> "mm⁴";
            case "Wx", "Wy" -> "mm³";
            default -> "mm";
        };
    }

    // ====================== FORMULAS ======================
    private Tab buildFormulasTab() {
        VBox cards = new VBox(8);
        cards.setPadding(new Insets(12, 4, 12, 4));
        cards.getChildren().add(description(
            "12 formule pre-impostate per calcoli meccanici tipici: tensioni σ assiale/flessione, "
            + "frecce trave (3 casi: appoggiata centrata, mensola, distribuito), Eulero buckling Pcr, "
            + "coppia bullone T=K·d·F, saldatura fillet, hoop tubo, potenza meccanica P=T·ω, "
            + "dilatazione termica, fattore di sicurezza η. Espandi una card → compila → Calcola → "
            + "'Ins' inserisce il risultato, '→ var' lo salva come variabile nominata."));
        cards.getChildren().addAll(
            formulaCard("σ = F / A — Tensione assiale",
                new String[][]{{"F", "Forza [N]"}, {"A", "Area [mm²]"}},
                vals -> new FormulaResult(vals[0]/vals[1], "MPa", "σ", "sigma_ax")),
            formulaCard("σ = M·y / I — Tensione di flessione",
                new String[][]{{"M", "Momento [N·mm]"}, {"y", "Distanza fibra [mm]"}, {"I", "Momento d'inerzia [mm⁴]"}},
                vals -> new FormulaResult(vals[0]*vals[1]/vals[2], "MPa", "σ", "sigma_b")),
            formulaCard("δ = F·L³ / (48·E·I) — Trave appoggiata, carico centrato",
                new String[][]{{"F", "Forza [N]"}, {"L", "Luce [mm]"}, {"E", "Modulo elastico [MPa]"}, {"I", "Inerzia [mm⁴]"}},
                vals -> new FormulaResult(vals[0]*Math.pow(vals[1],3)/(48.0*vals[2]*vals[3]), "mm", "δ", "delta_ss")),
            formulaCard("δ = F·L³ / (3·E·I) — Mensola, carico all'estremo",
                new String[][]{{"F", "Forza [N]"}, {"L", "Lunghezza [mm]"}, {"E", "Modulo elastico [MPa]"}, {"I", "Inerzia [mm⁴]"}},
                vals -> new FormulaResult(vals[0]*Math.pow(vals[1],3)/(3.0*vals[2]*vals[3]), "mm", "δ", "delta_cant")),
            formulaCard("δ = 5·q·L⁴ / (384·E·I) — Trave appoggiata, carico distribuito",
                new String[][]{{"q", "Carico [N/mm]"}, {"L", "Luce [mm]"}, {"E", "Modulo elastico [MPa]"}, {"I", "Inerzia [mm⁴]"}},
                vals -> new FormulaResult(5.0*vals[0]*Math.pow(vals[1],4)/(384.0*vals[2]*vals[3]), "mm", "δ", "delta_distr")),
            formulaCard("Pcr = π²·E·I / Lk² — Carico critico Eulero",
                new String[][]{{"E", "Modulo elastico [MPa]"}, {"I", "Inerzia minima [mm⁴]"}, {"Lk", "Lunghezza libera [mm]"}},
                vals -> new FormulaResult(Math.PI*Math.PI*vals[0]*vals[1]/(vals[2]*vals[2]), "N", "Pcr", "Pcr")),
            formulaCard("T = K·d·F — Coppia serraggio bullone",
                new String[][]{{"K", "Coeff. attrito (0.15 lub / 0.20 std / 0.30 sec)"}, {"d", "Diametro nominale [mm]"}, {"F", "Precarico [N]"}},
                vals -> new FormulaResult(vals[0]*vals[1]*vals[2], "N·mm", "T", "Tbullone")),
            formulaCard("σ = F / (0.707·a·L) — Saldatura fillet",
                new String[][]{{"F", "Forza [N]"}, {"a", "Lato cordone [mm]"}, {"L", "Lunghezza cordone [mm]"}},
                vals -> new FormulaResult(vals[0]/(0.707*vals[1]*vals[2]), "MPa", "σ", "sigma_weld")),
            formulaCard("σh = p·D / (2·t) — Sforzo cerchiante tubo",
                new String[][]{{"p", "Pressione interna [MPa]"}, {"D", "Diametro medio [mm]"}, {"t", "Spessore [mm]"}},
                vals -> new FormulaResult(vals[0]*vals[1]/(2.0*vals[2]), "MPa", "σh", "sigma_h")),
            formulaCard("P = T·2π·n/60 — Potenza meccanica",
                new String[][]{{"T", "Coppia [N·m]"}, {"n", "Velocità [rpm]"}},
                vals -> new FormulaResult(vals[0]*2*Math.PI*vals[1]/60.0, "W", "P", "P_mech")),
            formulaCard("ΔL = α·L·ΔT — Dilatazione termica",
                new String[][]{{"alpha", "Coeff. dilatazione [×10⁻⁶/K]"}, {"L", "Lunghezza [mm]"}, {"DT", "ΔTemperatura [K]"}},
                vals -> new FormulaResult(vals[0]*1e-6*vals[1]*vals[2], "mm", "ΔL", "dL")),
            formulaCard("η = σadm / σmax — Fattore di sicurezza",
                new String[][]{{"sadm", "Tensione ammissibile [MPa]"}, {"smax", "Tensione massima [MPa]"}},
                vals -> new FormulaResult(vals[0]/vals[1], "", "η", "eta"))
        );
        return new Tab("Formule", scroll(cards));
    }

    private record FormulaResult(double value, String unit, String name, String varName) {
        FormulaResult(double value, String unit, String name) { this(value, unit, name, sanitize(name)); }
        private static String sanitize(String s) {
            String r = s.replaceAll("[^A-Za-z0-9_]", "");
            if (r.isEmpty() || !Character.isLetter(r.charAt(0))) r = "x_" + r;
            return r;
        }
    }

    private TitledPane formulaCard(String title, String[][] inputs, java.util.function.Function<double[], FormulaResult> fn) {
        GridPane g = new GridPane();
        g.setHgap(6); g.setVgap(4);
        g.setPadding(new Insets(6, 0, 6, 0));
        for (int c = 0; c < 2; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(c == 0 ? 40 : 60);
            g.getColumnConstraints().add(cc);
        }
        TextField[] fields = new TextField[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            Label l = new Label(inputs[i][0]);
            l.getStyleClass().add("toolkit-key");
            Tooltip(l, inputs[i][1]);
            fields[i] = numField();
            fields[i].setPromptText(inputs[i][1]);
            g.add(l, 0, i);
            g.add(fields[i], 1, i);
        }
        Label result = new Label("—");
        result.getStyleClass().add("toolkit-result");
        result.setMaxWidth(Double.MAX_VALUE);

        Button compute = new Button("Calcola");
        compute.getStyleClass().add("eq-chip");
        Button ins = new Button("Inserisci");
        ins.getStyleClass().add("chip");
        Button var = new Button("→ var");
        var.getStyleClass().add("chip");
        HBox actions = new HBox(6, compute, ins, var);

        final double[] last = {Double.NaN};
        final String[] lastVarName = {""};
        compute.setOnAction(e -> {
            try {
                double[] v = new double[fields.length];
                for (int i = 0; i < fields.length; i++) v[i] = parseDouble(fields[i].getText());
                FormulaResult r = fn.apply(v);
                last[0] = r.value();
                lastVarName[0] = r.varName();
                String unitSuf = r.unit().isEmpty() ? "" : " " + r.unit();
                result.setText(r.name() + " = " + fmt(r.value()) + unitSuf + "    → var " + r.varName());
            } catch (Exception ex) {
                result.setText("input non valido");
            }
        });
        ins.setOnAction(e -> { if (!Double.isNaN(last[0])) bridge.insert(fmt(last[0])); });
        var.setOnAction(e -> {
            if (Double.isNaN(last[0]) || lastVarName[0].isEmpty()) return;
            bridge.setVariable(lastVarName[0], last[0]);
        });

        VBox content = new VBox(6, g, actions, result);
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(false);
        pane.getStyleClass().add("formula-card");
        return pane;
    }

    private static void Tooltip(Label l, String text) {
        l.setTooltip(new javafx.scene.control.Tooltip(text));
    }

    // ====================== WIZARDS (Bolts, Welds, Stats, Matrix, Time) ======================
    private Tab buildWizardsTab() {
        Accordion acc = new Accordion();
        acc.getPanes().addAll(
            buildBoltPane(),
            buildWeldPane(),
            buildStatsPane(),
            buildMatrixPane(),
            buildTimePane()
        );
        acc.setExpandedPane(acc.getPanes().get(0));
        VBox box = new VBox(8);
        box.getChildren().add(description(
            "Procedure guidate per dimensionamento: bulloni ISO M3-M48 con classi 4.6→12.9 "
            + "(restituisce d, As, coppia serraggio, precarico), saldature fillet con materiale "
            + "base, statistica su lista di numeri (media, σ, mediana, min/max), matrici 3×3 "
            + "(det, inversa, trasposta, somma, prodotto), aritmetica HH:MM:SS."));
        box.getChildren().add(acc);
        return new Tab("Procedure", scroll(box));
    }

    private TitledPane buildBoltPane() {
        VBox v = new VBox(6);
        v.setPadding(new Insets(6));

        ComboBox<Catalog.Bolt> threadCombo = new ComboBox<>(FXCollections.observableArrayList(Catalog.BOLTS));
        threadCombo.setMaxWidth(Double.MAX_VALUE);
        threadCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Catalog.Bolt b) { return b == null ? "" : b.designation(); }
            @Override public Catalog.Bolt fromString(String s) { return null; }
        });

        ComboBox<Catalog.BoltClass> classCombo = new ComboBox<>(FXCollections.observableArrayList(Catalog.BOLT_CLASSES));
        classCombo.setMaxWidth(Double.MAX_VALUE);
        classCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Catalog.BoltClass c) { return c == null ? "" : c.name(); }
            @Override public Catalog.BoltClass fromString(String s) { return null; }
        });

        TextField kField = new TextField("0.20");
        kField.getStyleClass().add("toolkit-input");

        GridPane out = new GridPane();
        out.setHgap(6); out.setVgap(4);
        out.getStyleClass().add("toolkit-table");
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(c == 0 ? 28 : c == 1 ? 42 : 30);
            out.getColumnConstraints().add(cc);
        }

        Runnable update = () -> {
            out.getChildren().clear();
            Catalog.Bolt b = threadCombo.getValue();
            Catalog.BoltClass cl = classCombo.getValue();
            if (b == null || cl == null) return;
            double k = 0.20;
            try { k = Double.parseDouble(kField.getText().trim().replace(",", ".")); } catch (Exception ignored) {}
            double Fp = 0.7 * cl.tensileMPa() * b.As(); // N
            double T  = k * b.d() * Fp;                 // N·mm
            int r = 0;
            addPropertyRow(out, r++, "d",        fmt(b.d())       + " mm",   "d_bolt",  b.d());
            addPropertyRow(out, r++, "passo",    fmt(b.pitch())   + " mm",   "pitch",   b.pitch());
            addPropertyRow(out, r++, "As",       fmt(b.As())      + " mm²",  "As",      b.As());
            addPropertyRow(out, r++, "d foro",   fmt(b.dTap())    + " mm",   "d_tap",   b.dTap());
            addPropertyRow(out, r++, "σy",       fmt(cl.yieldMPa())   + " MPa", "sy_bolt", cl.yieldMPa());
            addPropertyRow(out, r++, "σt",       fmt(cl.tensileMPa()) + " MPa", "su_bolt", cl.tensileMPa());
            addPropertyRow(out, r++, "F precar", fmt(Fp)          + " N",    "Fp",      Fp);
            addPropertyRow(out, r++, "T serr",   fmt(T/1000.0)    + " N·m",  "Tbolt",   T/1000.0);
        };
        threadCombo.valueProperty().addListener((o, ov, nv) -> update.run());
        classCombo.valueProperty().addListener((o, ov, nv) -> update.run());
        kField.textProperty().addListener((o, ov, nv) -> update.run());
        threadCombo.getSelectionModel().select(4);
        classCombo.getSelectionModel().select(2);

        v.getChildren().addAll(
            label("Filettatura ISO:"), threadCombo,
            label("Classe (es. 8.8):"), classCombo,
            label("Coeff. K (0.15 lub / 0.20 std / 0.30 sec):"), kField,
            out,
            hint("Precarico = 0.7·σt·As (UNI 5739). T = K·d·Fp.")
        );
        return new TitledPane("Bulloni ISO", v);
    }

    private TitledPane buildWeldPane() {
        VBox v = new VBox(6);
        v.setPadding(new Insets(6));

        TextField fField = numField(); fField.setPromptText("F [N]"); fField.setText("10000");
        TextField aField = numField(); aField.setPromptText("a [mm]"); aField.setText("4");
        TextField lField = numField(); lField.setPromptText("L [mm]"); lField.setText("100");

        ComboBox<Catalog.Material> matCombo = new ComboBox<>(FXCollections.observableArrayList(Catalog.MATERIALS));
        matCombo.setMaxWidth(Double.MAX_VALUE);
        matCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Catalog.Material m) { return m == null ? "" : m.name(); }
            @Override public Catalog.Material fromString(String s) { return null; }
        });
        matCombo.getSelectionModel().select(0);

        Label outSig = new Label("—");
        outSig.getStyleClass().add("toolkit-result");
        outSig.setMaxWidth(Double.MAX_VALUE);
        outSig.setWrapText(true);

        Runnable calc = () -> {
            try {
                double F = parseDouble(fField.getText());
                double a = parseDouble(aField.getText());
                double L = parseDouble(lField.getText());
                Catalog.Material mat = matCombo.getValue();
                double sigma = F / (a * L);
                double sigmaEq = sigma * Math.sqrt(3);
                String adm = mat == null ? "" :
                    String.format(Locale.ROOT, "  ·  σadm ≈ %s MPa", fmt(mat.tensileStress() * 0.45));
                outSig.setText("σ ≈ " + fmt(sigma) + " MPa  ·  σeq ≈ " + fmt(sigmaEq) + " MPa" + adm);
            } catch (Exception ex) {
                outSig.setText("input non valido");
            }
        };
        for (TextField f : new TextField[]{fField, aField, lField})
            f.textProperty().addListener((o, ov, nv) -> calc.run());
        matCombo.valueProperty().addListener((o, ov, nv) -> calc.run());
        calc.run();

        v.getChildren().addAll(
            label("F [N]:"), fField,
            label("a (gola) [mm]:"), aField,
            label("L (lunghezza) [mm]:"), lField,
            label("Materiale base:"), matCombo,
            outSig,
            hint("Fillet semplificato: σ=F/(a·L); σ_eq=σ·√3 (Von Mises, taglio puro). σadm ≈ 0.45·σt.")
        );
        return new TitledPane("Saldature fillet", v);
    }

    private TitledPane buildStatsPane() {
        VBox v = new VBox(6);
        v.setPadding(new Insets(6));

        TextArea input = new TextArea();
        input.setPromptText("Numeri, uno per riga o separati da spazi/virgole/punto-virgola.\nEs: 12.3 14.1 13.8");
        input.setPrefRowCount(4);
        input.getStyleClass().add("toolkit-input");

        GridPane out = new GridPane();
        out.setHgap(6); out.setVgap(4);
        out.getStyleClass().add("toolkit-table");
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(c == 0 ? 28 : c == 1 ? 42 : 30);
            out.getColumnConstraints().add(cc);
        }

        Runnable calc = () -> {
            out.getChildren().clear();
            String t = input.getText();
            if (t == null || t.isBlank()) return;
            String[] tokens = t.split("[\\s,;]+");
            List<Double> nums = new ArrayList<>();
            for (String tok : tokens) {
                if (tok.isBlank()) continue;
                try { nums.add(Double.parseDouble(tok.replace(",", "."))); } catch (Exception ignored) {}
            }
            if (nums.isEmpty()) return;
            int n = nums.size();
            double sum = 0;
            for (double d : nums) sum += d;
            double mean = sum / n;
            double var = 0;
            for (double d : nums) var += (d - mean) * (d - mean);
            var /= n;
            double sd = Math.sqrt(var);
            double sdSample = n > 1 ? Math.sqrt(var * n / (n - 1)) : 0;
            List<Double> sorted = new ArrayList<>(nums);
            sorted.sort(Double::compare);
            double mn = sorted.get(0);
            double mx = sorted.get(n - 1);
            double med = n % 2 == 0
                ? (sorted.get(n/2 - 1) + sorted.get(n/2)) / 2.0
                : sorted.get(n/2);

            int r = 0;
            addPropertyRow(out, r++, "n",       Integer.toString(n), "stat_n",   n);
            addPropertyRow(out, r++, "Σ",       fmt(sum),            "stat_sum", sum);
            addPropertyRow(out, r++, "media",   fmt(mean),           "stat_mu",  mean);
            addPropertyRow(out, r++, "mediana", fmt(med),            "stat_med", med);
            addPropertyRow(out, r++, "σ pop",   fmt(sd),             "stat_sd",  sd);
            addPropertyRow(out, r++, "σ camp",  fmt(sdSample),       "stat_sds", sdSample);
            addPropertyRow(out, r++, "min",     fmt(mn),             "stat_min", mn);
            addPropertyRow(out, r++, "max",     fmt(mx),             "stat_max", mx);
        };
        input.textProperty().addListener((o, ov, nv) -> calc.run());

        v.getChildren().addAll(label("Numeri:"), input, label("Statistiche:"), out);
        return new TitledPane("Statistica", v);
    }

    private TitledPane buildMatrixPane() {
        VBox v = new VBox(6);
        v.setPadding(new Insets(6));

        GridPane matA = matrixGrid(3);
        GridPane matB = matrixGrid(3);

        Label outLbl = new Label("—");
        outLbl.getStyleClass().add("toolkit-result");
        outLbl.setWrapText(true);
        outLbl.setMaxWidth(Double.MAX_VALUE);

        Button detA = new Button("det(A)");
        Button invA = new Button("inv(A)");
        Button trA  = new Button("Aᵀ");
        Button mul  = new Button("A × B");
        Button add  = new Button("A + B");
        Button sub  = new Button("A − B");
        for (Button bx : new Button[]{detA, invA, trA, mul, add, sub}) {
            bx.getStyleClass().add("chip");
            bx.setFocusTraversable(false);
        }

        detA.setOnAction(e -> {
            try { outLbl.setText("det = " + fmt(determinant3(readMatrix(matA, 3)))); }
            catch (Exception ex) { outLbl.setText("input non valido"); }
        });
        invA.setOnAction(e -> {
            try {
                double[][] inv = inverse3(readMatrix(matA, 3));
                if (inv == null) { outLbl.setText("matrice singolare"); return; }
                outLbl.setText(matrixToString(inv));
            } catch (Exception ex) { outLbl.setText("input non valido"); }
        });
        trA.setOnAction(e -> {
            try { outLbl.setText(matrixToString(transpose(readMatrix(matA, 3)))); }
            catch (Exception ex) { outLbl.setText("input non valido"); }
        });
        mul.setOnAction(e -> {
            try { outLbl.setText(matrixToString(multiply(readMatrix(matA, 3), readMatrix(matB, 3)))); }
            catch (Exception ex) { outLbl.setText("input non valido"); }
        });
        add.setOnAction(e -> {
            try { outLbl.setText(matrixToString(addMatrices(readMatrix(matA, 3), readMatrix(matB, 3), 1))); }
            catch (Exception ex) { outLbl.setText("input non valido"); }
        });
        sub.setOnAction(e -> {
            try { outLbl.setText(matrixToString(addMatrices(readMatrix(matA, 3), readMatrix(matB, 3), -1))); }
            catch (Exception ex) { outLbl.setText("input non valido"); }
        });

        HBox btnsA = new HBox(6, detA, invA, trA);
        HBox btnsAB = new HBox(6, add, sub, mul);

        v.getChildren().addAll(
            label("Matrice A (3×3):"), matA, btnsA,
            label("Matrice B (3×3):"), matB, btnsAB,
            outLbl
        );
        return new TitledPane("Matrici 3×3", v);
    }

    private GridPane matrixGrid(int n) {
        GridPane g = new GridPane();
        g.setHgap(3); g.setVgap(3);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                TextField f = new TextField(r == c ? "1" : "0");
                f.getStyleClass().add("toolkit-input");
                f.setPrefWidth(58);
                g.add(f, c, r);
            }
        }
        return g;
    }

    private double[][] readMatrix(GridPane g, int n) {
        double[][] m = new double[n][n];
        for (var node : g.getChildren()) {
            if (node instanceof TextField f) {
                Integer r = GridPane.getRowIndex(node);
                Integer c = GridPane.getColumnIndex(node);
                if (r == null || c == null) continue;
                m[r][c] = parseDouble(f.getText());
            }
        }
        return m;
    }

    private double determinant3(double[][] m) {
        return m[0][0]*(m[1][1]*m[2][2] - m[1][2]*m[2][1])
             - m[0][1]*(m[1][0]*m[2][2] - m[1][2]*m[2][0])
             + m[0][2]*(m[1][0]*m[2][1] - m[1][1]*m[2][0]);
    }

    private double[][] inverse3(double[][] m) {
        double d = determinant3(m);
        if (Math.abs(d) < 1e-12) return null;
        double[][] inv = new double[3][3];
        inv[0][0] = (m[1][1]*m[2][2] - m[1][2]*m[2][1]) / d;
        inv[0][1] = (m[0][2]*m[2][1] - m[0][1]*m[2][2]) / d;
        inv[0][2] = (m[0][1]*m[1][2] - m[0][2]*m[1][1]) / d;
        inv[1][0] = (m[1][2]*m[2][0] - m[1][0]*m[2][2]) / d;
        inv[1][1] = (m[0][0]*m[2][2] - m[0][2]*m[2][0]) / d;
        inv[1][2] = (m[0][2]*m[1][0] - m[0][0]*m[1][2]) / d;
        inv[2][0] = (m[1][0]*m[2][1] - m[1][1]*m[2][0]) / d;
        inv[2][1] = (m[0][1]*m[2][0] - m[0][0]*m[2][1]) / d;
        inv[2][2] = (m[0][0]*m[1][1] - m[0][1]*m[1][0]) / d;
        return inv;
    }

    private double[][] transpose(double[][] m) {
        int n = m.length;
        double[][] t = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) t[j][i] = m[i][j];
        return t;
    }

    private double[][] multiply(double[][] a, double[][] b) {
        int n = a.length;
        double[][] r = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                for (int k = 0; k < n; k++)
                    r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    private double[][] addMatrices(double[][] a, double[][] b, int sign) {
        int n = a.length;
        double[][] r = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) r[i][j] = a[i][j] + sign * b[i][j];
        return r;
    }

    private String matrixToString(double[][] m) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < m.length; i++) {
            s.append("| ");
            for (int j = 0; j < m[0].length; j++) {
                s.append(fmt(m[i][j])).append(j < m[0].length - 1 ? "  " : " |");
            }
            if (i < m.length - 1) s.append("\n");
        }
        return s.toString();
    }

    private TitledPane buildTimePane() {
        VBox v = new VBox(6);
        v.setPadding(new Insets(6));

        TextField t1h = numField(); t1h.setPrefWidth(45); t1h.setText("0");
        TextField t1m = numField(); t1m.setPrefWidth(45); t1m.setText("0");
        TextField t1s = numField(); t1s.setPrefWidth(45); t1s.setText("0");
        TextField t2h = numField(); t2h.setPrefWidth(45); t2h.setText("0");
        TextField t2m = numField(); t2m.setPrefWidth(45); t2m.setText("0");
        TextField t2s = numField(); t2s.setPrefWidth(45); t2s.setText("0");

        HBox r1 = new HBox(4, label("T1:"), t1h, label("h"), t1m, label("m"), t1s, label("s"));
        HBox r2 = new HBox(4, label("T2:"), t2h, label("h"), t2m, label("m"), t2s, label("s"));
        r1.setAlignment(Pos.CENTER_LEFT);
        r2.setAlignment(Pos.CENTER_LEFT);

        Label outT1   = new Label("T1 = —");   outT1.getStyleClass().add("toolkit-val");
        Label outAdd  = new Label("T1 + T2 = —"); outAdd.getStyleClass().add("toolkit-val");
        Label outSub  = new Label("T1 − T2 = —"); outSub.getStyleClass().add("toolkit-val");

        Runnable calc = () -> {
            try {
                long s1 = toSeconds(t1h, t1m, t1s);
                long s2 = toSeconds(t2h, t2m, t2s);
                outT1.setText(String.format(Locale.ROOT, "T1 = %d s · %.2f min · %.3f h", s1, s1/60.0, s1/3600.0));
                outAdd.setText("T1 + T2 = " + fmtTime(s1 + s2));
                outSub.setText("T1 − T2 = " + fmtTime(s1 - s2));
            } catch (Exception ex) {
                outT1.setText("input non valido"); outAdd.setText(""); outSub.setText("");
            }
        };
        for (TextField f : new TextField[]{t1h,t1m,t1s,t2h,t2m,t2s})
            f.textProperty().addListener((o, ov, nv) -> calc.run());
        calc.run();

        v.getChildren().addAll(r1, r2, outT1, outAdd, outSub);
        return new TitledPane("Tempo / Durata", v);
    }

    private long toSeconds(TextField h, TextField m, TextField s) {
        long hr = Long.parseLong(h.getText().trim());
        long mn = Long.parseLong(m.getText().trim());
        long sc = Long.parseLong(s.getText().trim());
        return hr * 3600 + mn * 60 + sc;
    }

    private String fmtTime(long sec) {
        long sign = sec < 0 ? -1 : 1;
        sec = Math.abs(sec);
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return (sign < 0 ? "−" : "") + String.format("%d:%02d:%02d", h, m, s);
    }

    // ====================== STRUMENTI ======================
    private Tab buildStrumentiTab() {
        Accordion acc = new Accordion();
        acc.getPanes().addAll(
            buildTrianglePane(),
            buildVectorsPane(),
            buildSolverPane(),
            buildVariablesPane()
        );
        acc.setExpandedPane(acc.getPanes().get(0));
        VBox box = new VBox(8);
        box.getChildren().add(description(
            "Risolutori e gestione: triangoli (qualunque combinazione SSS / SAS / SSA / ASA / AAS), "
            + "operazioni vettori 3D (modulo, prodotto scalare, vettoriale, angolo, proiezione), "
            + "solver f(x)=0 con Newton-Raphson (vede le variabili definite), tabella variabili nominate. "
            + "I tubi ASME B36.10 li trovi in Sezioni → Tubi."));
        box.getChildren().add(acc);
        return new Tab("Strumenti", scroll(box));
    }

    // --- Triangle solver ---
    private TitledPane buildTrianglePane() {
        GridPane g = new GridPane();
        g.setHgap(6); g.setVgap(4); g.setPadding(new Insets(6));
        for (int c = 0; c < 4; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            g.getColumnConstraints().add(cc);
        }
        TextField a = numField(), b = numField(), c = numField();
        TextField A = numField(), B = numField(), C = numField();
        addLabeled(g, 0, 0, "a [mm]", a);
        addLabeled(g, 0, 1, "b [mm]", b);
        addLabeled(g, 0, 2, "c [mm]", c);
        addLabeled(g, 1, 0, "A [°]",  A);
        addLabeled(g, 1, 1, "B [°]",  B);
        addLabeled(g, 1, 2, "C [°]",  C);

        Label result = new Label("Compila 3 valori (3 lati, oppure 2 lati + 1 angolo, oppure 1 lato + 2 angoli).");
        result.getStyleClass().add("toolkit-hint");
        result.setWrapText(true);

        Button solve = new Button("Risolvi");
        solve.getStyleClass().add("eq-chip");
        solve.setMaxWidth(Double.MAX_VALUE);
        solve.setOnAction(e -> {
            try {
                double[] v = solveTriangle(parseOpt(a), parseOpt(b), parseOpt(c),
                                            parseOpt(A), parseOpt(B), parseOpt(C));
                if (Double.isNaN(v[0])) { result.setText("Combinazione non risolvibile."); return; }
                if (Double.isNaN(parseOpt(a))) a.setText(fmt(v[0]));
                if (Double.isNaN(parseOpt(b))) b.setText(fmt(v[1]));
                if (Double.isNaN(parseOpt(c))) c.setText(fmt(v[2]));
                if (Double.isNaN(parseOpt(A))) A.setText(fmt(Math.toDegrees(v[3])));
                if (Double.isNaN(parseOpt(B))) B.setText(fmt(Math.toDegrees(v[4])));
                if (Double.isNaN(parseOpt(C))) C.setText(fmt(Math.toDegrees(v[5])));
                double area = 0.5 * v[0] * v[1] * Math.sin(v[5]);
                result.setText("Area ≈ " + fmt(area) + " mm² · perim ≈ " + fmt(v[0]+v[1]+v[2]) + " mm");
            } catch (Exception ex) {
                result.setText("Errore: " + ex.getMessage());
            }
        });

        Button clear = new Button("Pulisci");
        clear.getStyleClass().add("chip");
        clear.setOnAction(e -> { a.clear(); b.clear(); c.clear(); A.clear(); B.clear(); C.clear(); result.setText(""); });

        VBox content = new VBox(6, g, new HBox(6, solve, clear), result);
        return new TitledPane("Triangoli (qualunque caso)", content);
    }

    // a,b,c sides; A,B,C angles in radians. Returns full 6-tuple; first index NaN means failure.
    private double[] solveTriangle(double a, double b, double c, double A, double B, double C) {
        if (!Double.isNaN(A)) A = Math.toRadians(A);
        if (!Double.isNaN(B)) B = Math.toRadians(B);
        if (!Double.isNaN(C)) C = Math.toRadians(C);
        int known = 0;
        if (!Double.isNaN(a)) known++;
        if (!Double.isNaN(b)) known++;
        if (!Double.isNaN(c)) known++;
        if (!Double.isNaN(A)) known++;
        if (!Double.isNaN(B)) known++;
        if (!Double.isNaN(C)) known++;
        if (known < 3) return new double[]{Double.NaN,0,0,0,0,0};

        // SSS
        if (!Double.isNaN(a) && !Double.isNaN(b) && !Double.isNaN(c)) {
            A = Math.acos((b*b + c*c - a*a)/(2*b*c));
            B = Math.acos((a*a + c*c - b*b)/(2*a*c));
            C = Math.PI - A - B;
            return new double[]{a,b,c,A,B,C};
        }
        // SAS variants
        if (!Double.isNaN(a) && !Double.isNaN(b) && !Double.isNaN(C)) {
            c = Math.sqrt(a*a + b*b - 2*a*b*Math.cos(C));
            A = Math.acos((b*b + c*c - a*a)/(2*b*c));
            B = Math.PI - A - C;
            return new double[]{a,b,c,A,B,C};
        }
        if (!Double.isNaN(a) && !Double.isNaN(c) && !Double.isNaN(B)) {
            b = Math.sqrt(a*a + c*c - 2*a*c*Math.cos(B));
            A = Math.acos((b*b + c*c - a*a)/(2*b*c));
            C = Math.PI - A - B;
            return new double[]{a,b,c,A,B,C};
        }
        if (!Double.isNaN(b) && !Double.isNaN(c) && !Double.isNaN(A)) {
            a = Math.sqrt(b*b + c*c - 2*b*c*Math.cos(A));
            B = Math.acos((a*a + c*c - b*b)/(2*a*c));
            C = Math.PI - A - B;
            return new double[]{a,b,c,A,B,C};
        }
        // ASA / AAS
        int knownAng = 0;
        if (!Double.isNaN(A)) knownAng++;
        if (!Double.isNaN(B)) knownAng++;
        if (!Double.isNaN(C)) knownAng++;
        if (knownAng >= 2) {
            if (Double.isNaN(A)) A = Math.PI - B - C;
            if (Double.isNaN(B)) B = Math.PI - A - C;
            if (Double.isNaN(C)) C = Math.PI - A - B;
            // need at least one side
            double sa = !Double.isNaN(a) ? a / Math.sin(A) :
                        !Double.isNaN(b) ? b / Math.sin(B) :
                        !Double.isNaN(c) ? c / Math.sin(C) : Double.NaN;
            if (Double.isNaN(sa)) return new double[]{Double.NaN,0,0,0,0,0};
            if (Double.isNaN(a)) a = sa * Math.sin(A);
            if (Double.isNaN(b)) b = sa * Math.sin(B);
            if (Double.isNaN(c)) c = sa * Math.sin(C);
            return new double[]{a,b,c,A,B,C};
        }
        // SSA (ambiguous) — pick acute solution. Try all 6 permutations.
        double[] s;
        if ((s = ssaCore(a, A, b)) != null) return new double[]{a, b, s[2], A, s[0], s[1]};
        if ((s = ssaCore(a, A, c)) != null) return new double[]{a, s[2], c, A, s[1], s[0]};
        if ((s = ssaCore(b, B, a)) != null) return new double[]{a, b, s[2], s[0], B, s[1]};
        if ((s = ssaCore(b, B, c)) != null) return new double[]{s[2], b, c, s[1], B, s[0]};
        if ((s = ssaCore(c, C, a)) != null) return new double[]{a, s[2], c, s[0], s[1], C};
        if ((s = ssaCore(c, C, b)) != null) return new double[]{s[2], b, c, s[1], s[0], C};
        return new double[]{Double.NaN,0,0,0,0,0};
    }

    /** Core SSA: given known side ks opposite known angle kA, plus other side os,
     *  returns {oppositeAngleOfOs, thirdAngle, thirdSide} or null if no solution. */
    private double[] ssaCore(double ks, double kA, double os) {
        if (Double.isNaN(ks) || Double.isNaN(kA) || Double.isNaN(os)) return null;
        double sinX = os * Math.sin(kA) / ks;
        if (sinX > 1 + 1e-9) return null;
        double oA = Math.asin(Math.min(1.0, sinX));
        double tA = Math.PI - kA - oA;
        if (tA <= 0) return null;
        double ts = ks * Math.sin(tA) / Math.sin(kA);
        return new double[]{oA, tA, ts};
    }

    // --- Vectors ---
    private TitledPane buildVectorsPane() {
        GridPane g = new GridPane();
        g.setHgap(6); g.setVgap(4); g.setPadding(new Insets(6));
        for (int c = 0; c < 4; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            g.getColumnConstraints().add(cc);
        }
        TextField ax = numField(), ay = numField(), az = numField();
        TextField bx = numField(), by = numField(), bz = numField();
        Label aLbl = new Label("a"); aLbl.getStyleClass().add("toolkit-key");
        Label bLbl = new Label("b"); bLbl.getStyleClass().add("toolkit-key");
        g.add(aLbl, 0, 0); g.add(ax, 1, 0); g.add(ay, 2, 0); g.add(az, 3, 0);
        g.add(bLbl, 0, 1); g.add(bx, 1, 1); g.add(by, 2, 1); g.add(bz, 3, 1);

        Label magA = new Label("|a| = —"); magA.getStyleClass().add("toolkit-val");
        Label magB = new Label("|b| = —"); magB.getStyleClass().add("toolkit-val");
        Label dot  = new Label("a·b = —");  dot.getStyleClass().add("toolkit-val");
        Label cross= new Label("a×b = —"); cross.getStyleClass().add("toolkit-val");
        Label angle= new Label("angolo = —"); angle.getStyleClass().add("toolkit-val");
        Label proj = new Label("proj a su b = —"); proj.getStyleClass().add("toolkit-val");

        Button calc = new Button("Calcola");
        calc.getStyleClass().add("eq-chip");
        calc.setMaxWidth(Double.MAX_VALUE);
        calc.setOnAction(e -> {
            try {
                double[] a = {parseDouble(ax.getText()), parseDouble(ay.getText()), parseDouble(az.getText())};
                double[] b = {parseDouble(bx.getText()), parseDouble(by.getText()), parseDouble(bz.getText())};
                double la = Math.sqrt(a[0]*a[0] + a[1]*a[1] + a[2]*a[2]);
                double lb = Math.sqrt(b[0]*b[0] + b[1]*b[1] + b[2]*b[2]);
                double d  = a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
                double[] x = {a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0]};
                double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d/(la*lb)))));
                double p = d / lb;
                magA.setText("|a| = " + fmt(la));
                magB.setText("|b| = " + fmt(lb));
                dot.setText("a·b = " + fmt(d));
                cross.setText("a×b = (" + fmt(x[0]) + ", " + fmt(x[1]) + ", " + fmt(x[2]) + ")");
                angle.setText("angolo = " + fmt(ang) + "°");
                proj.setText("proj a su b = " + fmt(p));
            } catch (Exception ex) {
                magA.setText("input non valido"); magB.setText(""); dot.setText(""); cross.setText(""); angle.setText(""); proj.setText("");
            }
        });

        VBox out = new VBox(2, magA, magB, dot, cross, angle, proj);
        VBox content = new VBox(8, g, calc, out);
        return new TitledPane("Vettori 3D", content);
    }

    // --- Solver f(x)=0 ---
    private TitledPane buildSolverPane() {
        TextField expr = new TextField();
        expr.setPromptText("es: x^2 - 2  (per √2)");
        expr.getStyleClass().add("toolkit-input");

        TextField x0 = numField();
        x0.setText("1");

        Label result = new Label("—");
        result.getStyleClass().add("toolkit-result");

        Button solve = new Button("Risolvi f(x) = 0");
        solve.getStyleClass().add("eq-chip");
        solve.setMaxWidth(Double.MAX_VALUE);
        solve.setOnAction(e -> {
            try {
                double r = newton(expr.getText(), parseDouble(x0.getText()));
                result.setText("x ≈ " + fmt(r));
            } catch (Exception ex) {
                result.setText("Errore: " + ex.getMessage());
            }
        });

        Button ins = new Button("Inserisci x");
        ins.getStyleClass().add("chip");
        ins.setOnAction(e -> {
            try { bridge.insert(fmt(newton(expr.getText(), parseDouble(x0.getText())))); } catch (Exception ignored) {}
        });
        Button var = new Button("→ x");
        var.getStyleClass().add("chip");
        var.setOnAction(e -> {
            try { bridge.setVariable("x", newton(expr.getText(), parseDouble(x0.getText()))); } catch (Exception ignored) {}
        });

        VBox content = new VBox(6,
            label("Espressione in x:"), expr,
            label("Stima iniziale x0:"), x0,
            solve, result,
            new HBox(6, ins, var),
            hint("Newton-Raphson, derivata stimata numericamente. Le variabili definite sono disponibili.")
        );
        return new TitledPane("Solver  f(x) = 0", content);
    }

    private double newton(String expr, double x0) {
        if (expr == null || expr.isBlank()) throw new RuntimeException("manca f(x)");
        Map<String, Double> base = new LinkedHashMap<>(bridge.variables());
        double x = x0;
        double h = 1e-6 * Math.max(1, Math.abs(x));
        for (int i = 0; i < 200; i++) {
            base.put("x", x);
            double f = new CalculatorApp.Parser(expr, bridge.radians(), base).parse();
            base.put("x", x + h);
            double f2 = new CalculatorApp.Parser(expr, bridge.radians(), base).parse();
            double df = (f2 - f) / h;
            if (Math.abs(df) < 1e-14) throw new RuntimeException("derivata nulla");
            double dx = f / df;
            x -= dx;
            if (Math.abs(dx) < 1e-10) return x;
        }
        throw new RuntimeException("nessuna convergenza");
    }

    // --- Pipes ---
    private VBox buildPipesPanel() {
        Label desc = description(
            "Tubi commerciali ASME B36.10 (DN15 → DN300, schedule 40 e 80). Restituisce "
            + "diametro esterno OD, spessore, diametro interno ID, peso al metro, volume "
            + "interno L/m e designazione NPS (Nominal Pipe Size).");
        ComboBox<String> dnCombo = new ComboBox<>();
        ComboBox<String> schCombo = new ComboBox<>();
        List<String> dns = new ArrayList<>();
        List<String> schs = new ArrayList<>();
        for (Catalog.Pipe p : Catalog.PIPES) {
            if (!dns.contains(p.dn())) dns.add(p.dn());
            if (!schs.contains(p.sch())) schs.add(p.sch());
        }
        dnCombo.getItems().addAll(dns);
        schCombo.getItems().addAll(schs);
        dnCombo.setMaxWidth(Double.MAX_VALUE);
        schCombo.setMaxWidth(Double.MAX_VALUE);

        GridPane out = new GridPane();
        out.setHgap(6); out.setVgap(4);
        out.getStyleClass().add("toolkit-table");
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(c == 0 ? 28 : c == 1 ? 42 : 30);
            out.getColumnConstraints().add(cc);
        }

        Runnable update = () -> {
            out.getChildren().clear();
            String dn = dnCombo.getValue();
            String sch = schCombo.getValue();
            if (dn == null || sch == null) return;
            Catalog.Pipe p = Catalog.PIPES.stream()
                .filter(x -> x.dn().equals(dn) && x.sch().equals(sch))
                .findFirst().orElse(null);
            if (p == null) {
                out.add(new Label("Combinazione non disponibile"), 0, 0, 3, 1);
                return;
            }
            int r = 0;
            addPropertyRow(out, r++, "OD",   fmt(p.od())     + " mm",   "OD",       p.od());
            addPropertyRow(out, r++, "sp.",  fmt(p.wall())   + " mm",   "thk",      p.wall());
            addPropertyRow(out, r++, "ID",   fmt(p.id())     + " mm",   "ID",       p.id());
            addPropertyRow(out, r++, "peso", fmt(p.weight()) + " kg/m", "kg_per_m", p.weight());
            addPropertyRow(out, r++, "vol",  fmt(p.volPerM())+ " L/m",  "L_per_m",  p.volPerM());
            Label npsLbl = new Label("NPS");  npsLbl.getStyleClass().add("toolkit-key");
            Label npsVal = new Label(p.nps()); npsVal.getStyleClass().add("toolkit-val");
            out.add(npsLbl, 0, r);
            out.add(npsVal, 1, r, 2, 1);
        };
        dnCombo.valueProperty().addListener((o, ov, nv) -> update.run());
        schCombo.valueProperty().addListener((o, ov, nv) -> update.run());
        dnCombo.getSelectionModel().select(0);
        schCombo.getSelectionModel().select(0);

        return new VBox(6,
            desc,
            label("DN:"), dnCombo,
            label("Schedule:"), schCombo,
            label("Dati:"), out
        );
    }

    // --- Variables ---
    private TitledPane buildVariablesPane() {
        TableView<VarRow> table = new TableView<>();
        TableColumn<VarRow, String> nameCol = new TableColumn<>("Nome");
        TableColumn<VarRow, Double> valCol = new TableColumn<>("Valore");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        nameCol.setPrefWidth(80);
        valCol.setPrefWidth(140);
        valCol.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : fmt(v));
            }
        });
        TableColumn<VarRow, Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(60);
        actCol.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            private final Button del = new Button("✕");
            {
                del.getStyleClass().addAll("chip", "icon");
                del.setOnAction(e -> {
                    VarRow row = getTableView().getItems().get(getIndex());
                    bridge.removeVariable(row.getName());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : del);
            }
        });
        table.getColumns().addAll(nameCol, valCol, actCol);
        table.setPrefHeight(180);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessuna variabile."));

        Runnable refresh = () -> {
            List<VarRow> rows = new ArrayList<>();
            for (var e : bridge.variables().entrySet()) rows.add(new VarRow(e.getKey(), e.getValue()));
            table.setItems(FXCollections.observableArrayList(rows));
        };
        refresh.run();
        bridge.addVariableChangeListener(refresh);

        TextField name = new TextField();
        name.setPromptText("nome");
        name.getStyleClass().add("toolkit-input");
        TextField val = numField();
        val.setPromptText("valore");
        Button add = new Button("Aggiungi/Imposta");
        add.getStyleClass().add("chip");
        add.setOnAction(e -> {
            String n = name.getText().trim();
            if (n.isEmpty() || !Character.isLetter(n.charAt(0))) return;
            if (!n.matches("[A-Za-z][A-Za-z0-9_]*")) return;
            try { bridge.setVariable(n, parseDouble(val.getText())); name.clear(); val.clear(); } catch (Exception ignored) {}
        });
        HBox addRow = new HBox(6, name, val, add);
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox.setHgrow(val, Priority.ALWAYS);

        VBox content = new VBox(6, table, addRow,
            hint("Usa il nome direttamente in espressione, es: F*L/(48*E*I).")
        );
        return new TitledPane("Variabili", content);
    }

    public static class VarRow {
        private final SimpleStringProperty name;
        private final SimpleDoubleProperty value;
        public VarRow(String n, double v) {
            this.name = new SimpleStringProperty(n);
            this.value = new SimpleDoubleProperty(v);
        }
        public String getName() { return name.get(); }
        public Double getValue() { return value.get(); }
        public SimpleStringProperty nameProperty() { return name; }
        public SimpleDoubleProperty valueProperty() { return value; }
    }

    // ====================== Helpers ======================
    private ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("toolkit-scroll");
        return sp;
    }

    private static Label section(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("toolkit-section");
        return l;
    }

    private static Label label(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("toolkit-key");
        return l;
    }

    private static Label hint(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("toolkit-hint");
        l.setWrapText(true);
        return l;
    }

    private static Label description(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("tab-description");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static TextField numField() {
        TextField f = new TextField();
        f.getStyleClass().add("toolkit-input");
        return f;
    }

    private static void addLabeled(GridPane g, int col, int row, String label, TextField f) {
        Label l = new Label(label);
        l.getStyleClass().add("toolkit-key");
        VBox v = new VBox(2, l, f);
        g.add(v, col, row);
    }

    private static double parseDouble(String s) {
        if (s == null || s.isBlank()) throw new RuntimeException("vuoto");
        return Double.parseDouble(s.replace(",", ".").replace("−", "-").trim());
    }

    private static double parseOpt(TextField f) {
        try { return parseDouble(f.getText()); } catch (Exception e) { return Double.NaN; }
    }

    private static double parseOpt(String s) {
        try { return parseDouble(s); } catch (Exception e) { return Double.NaN; }
    }

    public static String fmt(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "−∞";
        if (v == 0) return "0";
        double av = Math.abs(v);
        if (av >= 0.001 && av < 1e7 && v == Math.floor(v)) return String.format(Locale.ROOT, "%.0f", v);
        if (av >= 0.001 && av < 1e7) {
            String s = String.format(Locale.ROOT, "%.6g", v);
            if (s.contains(".") && !s.toLowerCase().contains("e"))
                s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
            return s;
        }
        return String.format(Locale.ROOT, "%.4e", v);
    }
}
