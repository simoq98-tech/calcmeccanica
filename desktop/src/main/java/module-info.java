module calc {
    requires javafx.controls;
    requires javafx.base;
    requires javafx.graphics;
    requires java.desktop;

    exports calc;
    opens calc to javafx.graphics, javafx.base;
}
