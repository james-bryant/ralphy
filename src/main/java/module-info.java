module net.uberfoo.ai.ralphy {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.prefs;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.core;

    opens net.uberfoo.ai.ralphy to com.fasterxml.jackson.databind, javafx.fxml, spring.beans, spring.context, spring.core;
    exports net.uberfoo.ai.ralphy;
}
